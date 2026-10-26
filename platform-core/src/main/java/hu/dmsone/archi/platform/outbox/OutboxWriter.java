package hu.dmsone.archi.platform.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.tenant.TenantContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox: az eseményt ugyanabban a tranzakcióban írja az {@code outbox.outbox_event} táblába,
 * mint az üzleti adatot. A Kafkára küldést az outbox-relay végzi.
 */
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final ActorProvider actorProvider;
    private final ObjectProvider<Tracer> tracer;
    private final String serviceId;
    private final String topic;

    public OutboxWriter(DataSource tenantDataSource, ObjectMapper objectMapper, ActorProvider actorProvider,
                        ObjectProvider<Tracer> tracer, String serviceId, String topic) {
        this.jdbc = JdbcClient.create(tenantDataSource);
        this.objectMapper = objectMapper;
        this.actorProvider = actorProvider;
        this.tracer = tracer;
        this.serviceId = serviceId;
        this.topic = topic;
    }

    /**
     * Esemény rögzítése az outboxba. Csak aktív tranzakción belül hívható.
     *
     * @param before az entitás előző állapota (létrehozásnál null)
     * @param after  az entitás új állapota (törlésnél null)
     */
    public EventEnvelope record(String entity, String entityId, String action, long version,
                                Object before, Object after, EventEnvelope.Acl acl) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Az outbox írás csak tranzakción belül megengedett");
        }
        String tenant = TenantContext.require();
        EventEnvelope env = new EventEnvelope(
                UUID.randomUUID(), tenant, serviceId, entity, entityId, action, version,
                actorProvider.current(), Instant.now(), toJson(before), toJson(after),
                acl == null ? EventEnvelope.Acl.forEveryone() : acl, traceparent());
        try {
            jdbc.sql("""
                    insert into outbox.outbox_event (id, topic, msg_key, payload, created_at)
                    values (:id, :topic, :key, cast(:payload as jsonb), now())
                    """)
                    .param("id", env.eventId())
                    .param("topic", topic)
                    .param("key", tenant + ":" + entityId)
                    .param("payload", objectMapper.writeValueAsString(env))
                    .update();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Esemény szerializálása sikertelen", e);
        }
        return env;
    }

    private JsonNode toJson(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    private String traceparent() {
        Tracer t = tracer.getIfAvailable();
        if (t == null) {
            return null;
        }
        Span span = t.currentSpan();
        if (span == null) {
            return null;
        }
        return "00-" + span.context().traceId() + "-" + span.context().spanId() + "-01";
    }
}
