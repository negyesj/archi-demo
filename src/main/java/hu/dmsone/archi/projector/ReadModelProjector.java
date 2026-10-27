package hu.dmsone.archi.projector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.reporting.AclCriteria;
import hu.dmsone.archi.platform.reporting.TenantMongoTemplates;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantNames;
import hu.dmsone.archi.platform.trail.TrailKind;
import hu.dmsone.archi.platform.trail.TrailRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minden CRUD esemény után aszinkron módon frissíti a tenant riport adatbázisában ({@code rpt_<tenant>}) a
 * {@code <szolgáltatás>_<entitás>} kollekció dokumentumát: az entitás utolsó állapota + {@code _acl} (láthatóság).
 * Idempotens és sorrendtűrő: csak nagyobb verzió írja felül a dokumentumot.
 * Újraépítés: a consumer group offsetjének visszaállítása a topic elejére.
 */
@Component
public class ReadModelProjector {

    private static final Logger log = LoggerFactory.getLogger(ReadModelProjector.class);

    private final ObjectMapper objectMapper;
    private final TenantMongoTemplates mongo;
    private final Set<String> indexed = ConcurrentHashMap.newKeySet();
    private final TrailRecorder trail;

    public ReadModelProjector(ObjectMapper objectMapper, TenantMongoTemplates mongo, TrailRecorder trail) {
        this.objectMapper = objectMapper;
        this.mongo = mongo;
        this.trail = trail;
    }

    @KafkaListener(topicPattern = Topics.DOMAIN_PATTERN, groupId = "reporting-projector",
            properties = {"auto.offset.reset=earliest", "metadata.max.age.ms=30000"})
    public void onEvent(ConsumerRecord<String, String> rec) throws Exception {
        EventEnvelope e = objectMapper.readValue(rec.value(), EventEnvelope.class);
        // Trail: Kafka várakozás, a MongoDB írás ideje, e2e = mikor került ki a riport (NoSQL) adatbázisba
        String coll = collection(e.service(), e.entity());
        trail.consume(e, rec, () -> trail.step(TrailRecorder.Spec.of(TrailKind.MONGO, "upsert " + coll,
                TenantNames.mongoDatabase(e.tenantId()) + "." + coll).withE2eFrom(e.occurredAt()), () -> {
            TenantContext.runAs(e.tenantId(), () -> project(e));
            return null;
        }));
    }

    static String collection(String service, String entity) {
        String snake = entity.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        return service + "_" + snake;
    }

    private void project(EventEnvelope e) {
        MongoTemplate t = mongo.forTenant(e.tenantId());
        String coll = collection(e.service(), e.entity());
        ensureIndexes(e.tenantId(), t, coll);

        Document existing = t.findById(e.entityId(), Document.class, coll);
        if (existing != null && existing.get("_version") instanceof Number v && v.longValue() >= e.version()) {
            log.debug("Elavult esemény kihagyva: {} {} v{}", coll, e.entityId(), e.version());
            return;
        }
        if (e.after() == null) {
            t.remove(new Query(Criteria.where("_id").is(e.entityId())), coll);
            return;
        }
        Document doc = Document.parse(e.after().toString());
        doc.put("_id", e.entityId());
        doc.put("_version", e.version());
        doc.put("_updatedAt", Date.from(e.occurredAt()));
        doc.put("_lastAction", e.action());
        EventEnvelope.Acl acl = e.acl() == null ? EventEnvelope.Acl.forEveryone() : e.acl();
        doc.put(AclCriteria.FIELD, new Document("users", acl.users())
                .append("orgUnits", acl.orgUnits())
                .append("everyone", acl.everyone()));
        t.save(doc, coll);
        enrich(t, e);
    }

    /** Denormalizált mezők frissítése más szolgáltatások riport dokumentumaiban (pl. partner aktuális neve az iratokban). */
    private void enrich(MongoTemplate t, EventEnvelope e) {
        if ("partner".equals(e.service()) && "Partner".equals(e.entity()) && "UPDATE".equals(e.action())) {
            JsonNode name = e.after().path("name");
            t.updateMulti(new Query(Criteria.where("partnerId").is(e.entityId())),
                    new Update().set("partnerAktualisNev", name.asText()), "irat_kuldemeny");
        }
    }

    private void ensureIndexes(String tenant, MongoTemplate t, String coll) {
        if (!indexed.add(tenant + "/" + coll)) {
            return;
        }
        t.indexOps(coll).ensureIndex(new Index().on("_acl.users", Sort.Direction.ASC));
        t.indexOps(coll).ensureIndex(new Index().on("_acl.orgUnits", Sort.Direction.ASC));
        t.indexOps(coll).ensureIndex(new Index().on("_updatedAt", Sort.Direction.DESC));
        if ("irat_kuldemeny".equals(coll)) {
            t.indexOps(coll).ensureIndex(new Index().on("iktatoszam", Sort.Direction.ASC));
            t.indexOps(coll).ensureIndex(new Index().on("erkezesDatum", Sort.Direction.DESC));
            t.indexOps(coll).ensureIndex(new Index().on("partnerId", Sort.Direction.ASC));
            t.indexOps(coll).ensureIndex(new Index().on("ugyintezoId", Sort.Direction.ASC));
        }
    }
}
