package hu.dmsone.archi.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.dmsone.archi.platform.db.TenantMigrator;
import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Minden CRUD / üzleti eseményt fogad a csomagütemezőn (Kafka) keresztül, aszinkron módon, és a tenant
 * adatbázisának {@code audit} sémájába írja. Idempotens: ugyanaz az esemény kétszer nem kerül be.
 */
@Component
public class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private final ObjectMapper objectMapper;
    private final JdbcClient jdbc;
    private final TenantRegistry registry;
    private final TenantMigrator migrator;

    public AuditConsumer(ObjectMapper objectMapper, JdbcClient jdbc, TenantRegistry registry, TenantMigrator migrator) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.registry = registry;
        this.migrator = migrator;
    }

    @KafkaListener(topicPattern = Topics.DOMAIN_PATTERN, groupId = "audit",
            properties = {"auto.offset.reset=earliest", "metadata.max.age.ms=30000"})
    public void onEvent(ConsumerRecord<String, String> rec) throws Exception {
        EventEnvelope e = objectMapper.readValue(rec.value(), EventEnvelope.class);
        Optional<TenantInfo> tenant = registry.find(e.tenantId());
        if (tenant.isEmpty()) {
            throw new IllegalStateException("Ismeretlen tenant az eseményben: " + e.tenantId());
        }
        if (!migrator.isMigrated(e.tenantId())) {
            migrator.migrate(tenant.get());
        }
        String traceId = traceId(e.traceparent());
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            TenantContext.runAs(e.tenantId(), () -> insert(e, traceId));
        } finally {
            MDC.remove("traceId");
        }
    }

    private void insert(EventEnvelope e, String traceId) {
        int n = jdbc.sql("""
                insert into audit.audit_event (event_id, occurred_at, service, entity, entity_id, action, version,
                    actor_id, actor_name, actor_type, trace_id, before, after, changes)
                values (:eventId, :occurredAt, :service, :entity, :entityId, :action, :version,
                    :actorId, :actorName, :actorType, :traceId, cast(:before as jsonb), cast(:after as jsonb),
                    cast(:changes as jsonb))
                on conflict (event_id) do nothing
                """)
                .param("eventId", e.eventId())
                .param("occurredAt", Timestamp.from(e.occurredAt()))
                .param("service", e.service())
                .param("entity", e.entity())
                .param("entityId", e.entityId())
                .param("action", e.action())
                .param("version", e.version())
                .param("actorId", e.actor() == null ? null : e.actor().id())
                .param("actorName", e.actor() == null ? null : e.actor().name())
                .param("actorType", e.actor() == null ? null : e.actor().type())
                .param("traceId", traceId)
                .param("before", e.before() == null ? null : e.before().toString())
                .param("after", e.after() == null ? null : e.after().toString())
                .param("changes", diff(e.before(), e.after()).toString())
                .update();
        if (n > 0) {
            log.debug("Audit: {} {} {} {}", e.service(), e.entity(), e.entityId(), e.action());
        }
    }

    /** Mezőszintű változáslista: [{field, from, to}] a legfelső szintű mezőkre. */
    ArrayNode diff(JsonNode before, JsonNode after) {
        ArrayNode list = objectMapper.createArrayNode();
        TreeSet<String> fields = new TreeSet<>();
        collect(before, fields);
        collect(after, fields);
        for (String f : fields) {
            JsonNode b = before == null ? NullNode.getInstance() : before.path(f);
            JsonNode a = after == null ? NullNode.getInstance() : after.path(f);
            if (b.isMissingNode()) { b = NullNode.getInstance(); }
            if (a.isMissingNode()) { a = NullNode.getInstance(); }
            if (!b.equals(a) && !"version".equals(f) && !"updatedAt".equals(f)) {
                ObjectNode c = list.addObject();
                c.put("field", f);
                c.set("from", b);
                c.set("to", a);
            }
        }
        return list;
    }

    private static void collect(JsonNode node, TreeSet<String> fields) {
        if (node != null && node.isObject()) {
            Iterator<String> it = node.fieldNames();
            it.forEachRemaining(fields::add);
        }
    }

    private static String traceId(String traceparent) {
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        return parts.length >= 3 ? parts[1] : null;
    }
}
