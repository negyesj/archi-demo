package hu.dmsone.archi.outboxrelay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.db.TenantMigrator;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import hu.dmsone.archi.platform.trail.TrailKind;
import hu.dmsone.archi.platform.trail.TrailRecorder;
import hu.dmsone.archi.platform.trail.TrailStep;
import org.springframework.beans.factory.ObjectProvider;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A tenant adatbázisok {@code outbox.outbox_event} tábláiból Kafkára publikál, sorrendtartóan.
 * Tenantonként egyszerre csak egy példány dolgozik (tranzakciós advisory lock), így több relay példány
 * esetén sem cserélődik fel ugyanannak az entitásnak két eseménye. Hibánál megáll az adott tenantnál
 * (a sorrend miatt), és a következő körben újrapróbálja.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long LOCK_KEY = 7_301_001L;

    private record Row(UUID id, long seq, String topic, String key, String payload, Instant createdAt) { }

    private final TenantRegistry registry;
    private final TenantMigrator migrator;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;
    private final ObjectMapper objectMapper;
    private final TrailRecorder trail;

    public OutboxRelay(TenantRegistry registry, TenantMigrator migrator, JdbcClient jdbc, TransactionTemplate tx,
                       KafkaTemplate<String, String> kafka, @Value("${relay.batch-size:200}") int batchSize,
                       ObjectMapper objectMapper, ObjectProvider<TrailRecorder> trail) {
        this.objectMapper = objectMapper;
        this.trail = trail.getIfAvailable();
        this.registry = registry;
        this.migrator = migrator;
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${relay.poll-interval-ms:500}")
    public void poll() {
        for (TenantInfo tenant : registry.all()) {
            if (tenant.status() == null || !migrator.isMigrated(tenant.code())) {
                continue;
            }
            try {
                int sent;
                do {
                    sent = TenantContext.callAs(tenant.code(), this::relayBatch);
                } while (sent >= batchSize);
            } catch (Exception e) {
                log.error("Outbox relay hiba, tenant: {}", tenant.code(), e);
            }
        }
    }

    private int relayBatch() {
        Integer n = tx.execute(status -> {
            Boolean locked = jdbc.sql("select pg_try_advisory_xact_lock(:k)").param("k", LOCK_KEY)
                    .query(Boolean.class).single();
            if (!Boolean.TRUE.equals(locked)) {
                return 0;
            }
            List<Row> rows = jdbc.sql("""
                    select id, seq, topic, msg_key, payload::text as payload, created_at from outbox.outbox_event
                    where published_at is null order by seq limit :limit
                    """).param("limit", batchSize)
                    .query((rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getLong("seq"), rs.getString("topic"),
                            rs.getString("msg_key"), rs.getString("payload"), rs.getTimestamp("created_at").toInstant()))
                    .list();
            int ok = 0;
            for (Row r : rows) {
                Instant sendStart = Instant.now();
                long t0 = System.nanoTime();
                try {
                    ProducerRecord<String, String> rec = new ProducerRecord<>(r.topic(), r.key(), r.payload());
                    rec.headers().add(new RecordHeader("tenantId", TenantContext.require().getBytes(StandardCharsets.UTF_8)));
                    kafka.send(rec).get(10, TimeUnit.SECONDS);
                    trailPublish(r, sendStart, t0, null);
                    jdbc.sql("update outbox.outbox_event set published_at = now(), attempts = attempts + 1 where id = :id")
                            .param("id", r.id()).update();
                    ok++;
                } catch (Exception e) {
                    log.warn("Kafka küldés sikertelen (seq {}), a tenant sora megáll: {}", r.seq(), e.getMessage());
                    trailPublish(r, sendStart, t0, TrailRecorder.errorText(e));
                    jdbc.sql("update outbox.outbox_event set attempts = attempts + 1, last_error = :err where id = :id")
                            .param("id", r.id()).param("err", String.valueOf(e.getMessage())).update();
                    break;
                }
            }
            return ok;
        });
        return n == null ? 0 : n;
    }

    /**
     * Trail lépés a küldésről: a várakozás az outbox sor létrejöttétől (az üzleti tranzakciótól) a küldés kezdetéig,
     * az időtartam a Kafka visszaigazolásáig tart. A trace az esemény {@code traceparent} mezőjéből jön.
     */
    private void trailPublish(Row r, Instant sendStart, long t0, String error) {
        if (trail == null) {
            return;
        }
        try {
            JsonNode env = objectMapper.readTree(r.payload());
            String[] ids = TrailRecorder.traceparent(env.path("traceparent").asText(null));
            if (ids == null) {
                return;
            }
            Instant occurred = env.hasNonNull("occurredAt") ? Instant.parse(env.get("occurredAt").asText()) : r.createdAt();
            long duration = (System.nanoTime() - t0) / 1_000_000;
            String entity = env.path("service").asText("") + "." + env.path("entity").asText("") + " " + env.path("action").asText("");
            trail.record(new TrailStep(TenantContext.require(), ids[0], TrailRecorder.newId(8), ids[1], trail.service(),
                    TrailKind.KAFKA_PUBLISH, "publish " + entity.strip(), r.topic(), sendStart, duration,
                    Math.max(0, sendStart.toEpochMilli() - r.createdAt().toEpochMilli()),
                    Math.max(0, sendStart.toEpochMilli() + duration - occurred.toEpochMilli()),
                    error == null ? "OK" : "ERROR", error, false, null));
        } catch (Exception e) {
            log.debug("Trail lépés kihagyva: {}", e.getMessage());
        }
    }

    /** A már publikált sorok takarítása (alapértelmezés: 7 nap után). */
    @Scheduled(cron = "${relay.cleanup-cron:0 30 3 * * *}")
    public void cleanup() {
        for (TenantInfo tenant : registry.active()) {
            if (!migrator.isMigrated(tenant.code())) {
                continue;
            }
            TenantContext.runAs(tenant.code(), () -> jdbc.sql(
                    "delete from outbox.outbox_event where published_at < now() - interval '7 days'").update());
        }
    }
}
