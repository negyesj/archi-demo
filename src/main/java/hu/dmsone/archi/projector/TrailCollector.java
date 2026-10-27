package hu.dmsone.archi.projector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.InsertManyOptions;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.reporting.TenantMongoTemplates;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import hu.dmsone.archi.platform.trail.TrailStep;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A műveleti trail gyűjtője: a {@code platform.trail} topicról a lépéseket a tenant riport adatbázisába írja
 * ({@code rpt_<tenant>.trail_step}, TTL: {@code trail.retention-days}, alapértelmezés 14 nap), és közben
 * órás összesítőt vezet ({@code trail_stat_hour}: darab, idő összeg/max, várakozás, e2e, hibák; 400 napig).
 * A napi összesítést a lekérdezés az órásból számolja.
 */
@Component
public class TrailCollector {

    public static final String STEPS = "trail_step";
    public static final String HOURLY = "trail_stat_hour";

    private static final TypeReference<List<TrailStep>> STEP_LIST = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final TenantMongoTemplates mongo;
    private final TenantRegistry registry;
    private final Duration retention;
    private final Set<String> indexed = ConcurrentHashMap.newKeySet();

    public TrailCollector(ObjectMapper objectMapper, TenantMongoTemplates mongo, TenantRegistry registry,
                          @Value("${trail.retention-days:14}") int retentionDays) {
        this.objectMapper = objectMapper;
        this.mongo = mongo;
        this.registry = registry;
        this.retention = Duration.ofDays(retentionDays);
    }

    private static final class Agg {
        Date hour;
        String service;
        String kind;
        String operation;
        long count;
        long sumMs;
        long maxMs;
        long errors;
        long queueCount;
        long sumQueueMs;
        long e2eCount;
        long sumE2eMs;
    }

    @KafkaListener(topics = Topics.TRAIL, groupId = "trail-collector", properties = {"auto.offset.reset=earliest"})
    public void onBatch(ConsumerRecord<String, String> rec) throws Exception {
        String tenant = rec.key();
        if (tenant == null || rec.value() == null || registry.find(tenant).isEmpty()) {
            return;
        }
        List<TrailStep> steps = objectMapper.readValue(rec.value(), STEP_LIST);
        if (steps.isEmpty()) {
            return;
        }
        MongoTemplate t = mongo.forTenant(tenant);
        ensureIndexes(tenant, t);
        t.getCollection(STEPS).insertMany(steps.stream().map(TrailCollector::toDocument).toList(),
                new InsertManyOptions().ordered(false));

        Map<String, Agg> byKey = new LinkedHashMap<>();
        for (TrailStep s : steps) {
            Instant hour = s.startedAt().truncatedTo(ChronoUnit.HOURS);
            String key = hour.toEpochMilli() + "|" + s.service() + "|" + s.kind() + "|" + s.operation();
            Agg a = byKey.computeIfAbsent(key, k -> {
                Agg n = new Agg();
                n.hour = Date.from(hour);
                n.service = s.service();
                n.kind = s.kind();
                n.operation = s.operation();
                return n;
            });
            a.count++;
            a.sumMs += s.durationMs();
            a.maxMs = Math.max(a.maxMs, s.durationMs());
            if ("ERROR".equals(s.status())) {
                a.errors++;
            }
            if (s.queueMs() != null) {
                a.queueCount++;
                a.sumQueueMs += s.queueMs();
            }
            if (s.e2eMs() != null) {
                a.e2eCount++;
                a.sumE2eMs += s.e2eMs();
            }
        }
        BulkOperations bulk = t.bulkOps(BulkOperations.BulkMode.UNORDERED, HOURLY);
        for (Map.Entry<String, Agg> e : byKey.entrySet()) {
            Agg a = e.getValue();
            Update u = new Update()
                    .setOnInsert("hour", a.hour)
                    .setOnInsert("service", a.service)
                    .setOnInsert("kind", a.kind)
                    .setOnInsert("operation", a.operation)
                    .inc("count", a.count)
                    .inc("sumMs", a.sumMs)
                    .max("maxMs", a.maxMs)
                    .inc("errors", a.errors)
                    .inc("queueCount", a.queueCount)
                    .inc("sumQueueMs", a.sumQueueMs)
                    .inc("e2eCount", a.e2eCount)
                    .inc("sumE2eMs", a.sumE2eMs);
            bulk.upsert(new Query(Criteria.where("_id").is(e.getKey())), u);
        }
        bulk.execute();
    }

    static Document toDocument(TrailStep s) {
        Document d = new Document("traceId", s.traceId())
                .append("spanId", s.spanId())
                .append("parentId", s.parentId())
                .append("service", s.service())
                .append("kind", s.kind())
                .append("operation", s.operation())
                .append("target", s.target())
                .append("startedAt", Date.from(s.startedAt()))
                .append("endedAt", Date.from(s.startedAt().plusMillis(s.durationMs())))
                .append("durationMs", s.durationMs())
                .append("status", s.status())
                .append("root", s.root());
        if (s.queueMs() != null) {
            d.append("queueMs", s.queueMs());
        }
        if (s.e2eMs() != null) {
            d.append("e2eMs", s.e2eMs());
        }
        if (s.detail() != null) {
            d.append("detail", s.detail());
        }
        if (s.user() != null) {
            d.append("user", s.user());
        }
        return d;
    }

    private void ensureIndexes(String tenant, MongoTemplate t) {
        if (!indexed.add(tenant)) {
            return;
        }
        t.indexOps(STEPS).ensureIndex(new Index().on("startedAt", Sort.Direction.ASC).expire(retention).named("ttl_startedAt"));
        t.indexOps(STEPS).ensureIndex(new Index().on("traceId", Sort.Direction.ASC));
        t.indexOps(STEPS).ensureIndex(new Index().on("root", Sort.Direction.ASC).on("startedAt", Sort.Direction.DESC));
        t.indexOps(HOURLY).ensureIndex(new Index().on("hour", Sort.Direction.ASC).expire(Duration.ofDays(400)).named("ttl_hour"));
        t.indexOps(HOURLY).ensureIndex(new Index().on("kind", Sort.Direction.ASC).on("hour", Sort.Direction.DESC));
    }
}
