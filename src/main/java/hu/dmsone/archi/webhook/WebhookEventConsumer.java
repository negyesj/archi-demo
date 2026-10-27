package hu.dmsone.archi.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.dmsone.archi.platform.db.TenantMigrator;
import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import hu.dmsone.archi.platform.trail.TrailRecorder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Az eseményekből kézbesítési sort képez az illeszkedő feliratkozásokhoz. A kifelé küldött üzenet publikus
 * formátumú (nem a belső boríték), eseménytípusa {@code <szolgáltatás>.<entitás>.<művelet>}.
 */
@Component
public class WebhookEventConsumer {

    private final ObjectMapper objectMapper;
    private final SubscriptionService subscriptions;
    private final JdbcClient jdbc;
    private final TenantRegistry registry;
    private final TenantMigrator migrator;
    private final TrailRecorder trail;

    public WebhookEventConsumer(ObjectMapper objectMapper, SubscriptionService subscriptions, JdbcClient jdbc,
                                TenantRegistry registry, TenantMigrator migrator, TrailRecorder trail) {
        this.trail = trail;
        this.objectMapper = objectMapper;
        this.subscriptions = subscriptions;
        this.jdbc = jdbc;
        this.registry = registry;
        this.migrator = migrator;
    }

    public static String eventType(EventEnvelope e) {
        return (e.service() + "." + e.entity() + "." + e.action()).toLowerCase(Locale.ROOT);
    }

    @KafkaListener(topicPattern = Topics.DOMAIN_PATTERN, groupId = "webhook-sender",
            properties = {"auto.offset.reset=latest", "metadata.max.age.ms=30000"})
    public void onEvent(ConsumerRecord<String, String> rec) throws Exception {
        EventEnvelope e = objectMapper.readValue(rec.value(), EventEnvelope.class);
        var tenant = registry.findActive(e.tenantId());
        if (tenant.isEmpty()) {
            return;
        }
        if (!migrator.isMigrated(e.tenantId())) {
            migrator.migrate(tenant.get());
        }
        String type = eventType(e);
        String[] trace = TrailRecorder.traceparent(e.traceparent());
        trail.consume(e, rec, () -> {
            TenantContext.runAs(e.tenantId(), () -> {
                for (SubscriptionService.Subscription s : subscriptions.matching(type)) {
                    jdbc.sql("""
                            insert into webhook.delivery (id, subscription_id, event_id, event_type, payload, status,
                                trace_id, parent_span_id, event_occurred_at)
                            values (:id, :sid, :eid, :type, cast(:payload as jsonb), 'PENDING', :traceId, :parent, :occurred)
                            on conflict (subscription_id, event_id) do nothing
                            """).param("id", UUID.randomUUID()).param("sid", s.id()).param("eid", e.eventId())
                            .param("type", type).param("payload", publicPayload(e, type))
                            .param("traceId", trace == null ? null : trace[0])
                            .param("parent", trace == null ? null : trace[1])
                            .param("occurred", java.sql.Timestamp.from(e.occurredAt()))
                            .update();
                }
            });
            return null;
        });
    }

    private String publicPayload(EventEnvelope e, String type) {
        ObjectNode p = objectMapper.createObjectNode();
        p.put("id", e.eventId().toString());
        p.put("type", type);
        p.put("occurredAt", e.occurredAt().toString());
        p.put("tenant", e.tenantId());
        p.put("entity", e.entity());
        p.put("entityId", e.entityId());
        p.put("action", e.action());
        p.put("version", e.version());
        if (e.actor() != null) {
            p.putObject("actor").put("type", e.actor().type()).put("name", e.actor().name());
        }
        p.set("data", e.after() != null ? e.after() : e.before());
        return p.toString();
    }
}
