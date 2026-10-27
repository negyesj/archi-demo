package hu.dmsone.archi.platform.trail;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.kafka.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A műveleti trail rögzítője. A lépéseket memóriában gyűjti, és másodpercenként, tenantonként csoportosítva a
 * {@code platform.trail} topicra küldi (a reporting-projector írja a tenant MongoDB-jébe). Az üzleti kérést nem
 * lassítja: a küldés aszinkron, túlterhelésnél a legújabb lépéseket eldobja (számlálóval).
 */
public class TrailRecorder implements DisposableBean {

    /** Mérendő munka; ellenőrzött kivételt is dobhat, amit a rögzítő változatlanul továbbad. */
    @FunctionalInterface
    public interface Work<T> {
        T call() throws Exception;
    }

    /**
     * A lépés leírása.
     *
     * @param e2eFrom ha meg van adva, a lépés végén {@code e2eMs = vége - e2eFrom} (pl. az esemény keletkezése)
     */
    public record Spec(String kind, String operation, String target, Long queueMs, Instant e2eFrom, String user, boolean root) {
        public static Spec of(String kind, String operation, String target) {
            return new Spec(kind, operation, target, null, null, null, false);
        }

        public Spec withQueue(Long ms) {
            return new Spec(kind, operation, target, ms, e2eFrom, user, root);
        }

        public Spec withE2eFrom(Instant from) {
            return new Spec(kind, operation, target, queueMs, from, user, root);
        }

        public Spec withUser(String u) {
            return new Spec(kind, operation, target, queueMs, e2eFrom, u, root);
        }
    }

    /** Egy megnyitott lépés; a {@link #end} zárja le. */
    public static final class Handle {
        private final String tenant;
        private final String traceId;
        private final String spanId;
        private final String parentId;
        private final Instant startedAt;
        private final long startNanos;
        private final TrailContext.Current previous;

        private Handle(String tenant, String traceId, String spanId, String parentId, TrailContext.Current previous) {
            this.tenant = tenant;
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentId = parentId;
            this.startedAt = Instant.now();
            this.startNanos = System.nanoTime();
            this.previous = previous;
        }

        public String spanId() {
            return spanId;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(TrailRecorder.class);
    private static final int MAX_QUEUE = 20_000;
    private static final int MAX_BATCH = 500;

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final String service;
    private final boolean enabled;
    private final ConcurrentLinkedQueue<TrailStep> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    private final ScheduledExecutorService flusher;

    public TrailRecorder(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper, String service,
                         long flushIntervalMs, boolean enabled) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.service = service;
        this.enabled = enabled;
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trail-flush");
            t.setDaemon(true);
            return t;
        });
        flusher.scheduleWithFixedDelay(this::flushSafely, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public String service() {
        return service;
    }

    /** Kikapcsolt trailnél ({@code platform.trail.enabled=false}) semmi nem rögzül. */
    public boolean enabled() {
        return enabled;
    }

    /** Új lépés nyitása a megadott művelet (trace) alatt; a szálon beágyazott lépések ehhez kapcsolódnak. */
    public Handle begin(String tenant, String traceId, String parentId) {
        String spanId = newId(8);
        String trace = traceId != null ? traceId : newId(16);
        TrailContext.Current prev = TrailContext.set(tenant == null ? null : new TrailContext.Current(tenant, trace, spanId));
        return new Handle(tenant, trace, spanId, parentId, prev);
    }

    /** Lépés nyitása az aktuális kontextuson belül (beágyazott lépés); kontextus nélkül null. */
    public Handle beginNested() {
        TrailContext.Current c = TrailContext.get();
        return c == null || !enabled ? null : begin(c.tenant(), c.traceId(), c.spanId());
    }

    public void end(Handle h, Spec spec, String status, String detail) {
        if (h == null) {
            return;
        }
        TrailContext.set(h.previous);
        long durationMs = (System.nanoTime() - h.startNanos) / 1_000_000;
        Long e2e = spec.e2eFrom() == null ? null
                : Math.max(0, h.startedAt.plusMillis(durationMs).toEpochMilli() - spec.e2eFrom().toEpochMilli());
        record(new TrailStep(h.tenant, h.traceId, h.spanId, h.parentId, service, spec.kind(), spec.operation(),
                spec.target(), h.startedAt, durationMs, spec.queueMs(), e2e, status, detail, spec.root(), spec.user()));
    }

    /** Önálló művelet (pl. Kafka üzenet feldolgozása) mérése a megadott trace alatt. */
    public <T> T operation(String tenant, String traceId, String parentId, Spec spec, Work<T> work) {
        return measure(begin(tenant, traceId, parentId), spec, work);
    }

    /**
     * Egy domain esemény feldolgozásának mérése egy Kafka fogyasztóban, az eseményt kiváltó művelet trace-ében.
     * Várakozás: a Kafka üzenet időbélyegétől a feldolgozás kezdetéig; e2e: az üzleti művelettől a feldolgozás végéig.
     */
    public <T> T consume(EventEnvelope e, ConsumerRecord<?, ?> rec, Work<T> work) {
        String[] ids = traceparent(e.traceparent());
        if (!enabled || ids == null || e.tenantId() == null) {
            return call(work);
        }
        Long queue = rec.timestamp() > 0 ? Math.max(0, System.currentTimeMillis() - rec.timestamp()) : null;
        Spec spec = new Spec(TrailKind.KAFKA_CONSUME, "consume " + e.service() + "." + e.entity() + " " + e.action(),
                rec.topic(), queue, e.occurredAt(), null, false);
        return operation(e.tenantId(), ids[0], ids[1], spec, work);
    }

    /** Beágyazott lépés mérése (pl. MongoDB írás egy fogyasztón belül); kontextus nélkül csak lefuttatja. */
    public <T> T step(Spec spec, Work<T> work) {
        Handle h = beginNested();
        if (h == null) {
            return call(work);
        }
        return measure(h, spec, work);
    }

    private <T> T measure(Handle h, Spec spec, Work<T> work) {
        try {
            T result = work.call();
            end(h, spec, "OK", null);
            return result;
        } catch (Throwable e) {
            end(h, spec, "ERROR", errorText(e));
            throw sneaky(e);
        }
    }

    /** Kész lépés rögzítése (pl. ha a mérés máshol történt). Tenant vagy trace nélkül nem rögzül. */
    public void record(TrailStep step) {
        if (!enabled || step.tenant() == null || step.traceId() == null) {
            return;
        }
        if (size.incrementAndGet() > MAX_QUEUE) {
            size.decrementAndGet();
            if (dropped.incrementAndGet() % 1000 == 1) {
                log.warn("Trail puffer megtelt, eddig {} lépés eldobva", dropped.get());
            }
            return;
        }
        queue.add(step);
    }

    public static String errorText(Throwable e) {
        String msg = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return msg.length() > 300 ? msg.substring(0, 300) : msg;
    }

    /** W3C traceparent → {traceId, spanId}; érvénytelen értékre null. */
    public static String[] traceparent(String tp) {
        if (tp == null) {
            return null;
        }
        String[] p = tp.split("-");
        return p.length >= 3 && p[1].length() == 32 ? new String[]{p[1], p[2]} : null;
    }

    public static String newId(int bytes) {
        byte[] b = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    void flushSafely() {
        try {
            flush();
        } catch (Exception e) {
            log.warn("Trail küldés sikertelen: {}", e.getMessage());
        }
    }

    void flush() throws Exception {
        if (queue.isEmpty()) {
            return;
        }
        Map<String, List<TrailStep>> byTenant = new HashMap<>();
        TrailStep s;
        while ((s = queue.poll()) != null) {
            size.decrementAndGet();
            byTenant.computeIfAbsent(s.tenant(), k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<String, List<TrailStep>> e : byTenant.entrySet()) {
            List<TrailStep> list = e.getValue();
            for (int i = 0; i < list.size(); i += MAX_BATCH) {
                String json = objectMapper.writeValueAsString(list.subList(i, Math.min(list.size(), i + MAX_BATCH)));
                kafka.send(Topics.TRAIL, e.getKey(), json);
            }
        }
    }

    @Override
    public void destroy() {
        flusher.shutdown();
        flushSafely();
    }

    private static <T> T call(Work<T> work) {
        try {
            return work.call();
        } catch (Throwable e) {
            throw sneaky(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneaky(Throwable e) throws E {
        throw (E) e;
    }
}
