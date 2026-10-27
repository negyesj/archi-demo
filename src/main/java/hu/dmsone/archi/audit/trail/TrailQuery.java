package hu.dmsone.archi.audit.trail;

import com.mongodb.client.MongoCollection;
import hu.dmsone.archi.platform.reporting.TenantMongoTemplates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.regex;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;

/**
 * A műveleti trail lekérdezései a tenant riport adatbázisából ({@code trail_step}, {@code trail_stat_hour});
 * a gyűjtő a reporting-projector.
 */
@Service
public class TrailQuery {

    static final String STEPS = "trail_step";
    static final String HOURLY = "trail_stat_hour";
    private static final String ZONE = "Europe/Budapest";

    private final TenantMongoTemplates mongo;

    public TrailQuery(TenantMongoTemplates mongo) {
        this.mongo = mongo;
    }

    public record OperationFilter(Instant from, Instant to, String service, String q, String user, Long minMs, boolean errorsOnly) { }

    public record Operation(String traceId, Instant startedAt, String operation, String service, String user,
                            long durationMs, Long completeMs, String status, int steps, List<String> services,
                            int errors, Map<String, Long> byKind) { }

    public record Page<T>(List<T> items, long total, int page, int size) { }

    public record Step(String spanId, String parentId, String service, String kind, String operation, String target,
                       Instant startedAt, long offsetMs, long durationMs, Long queueMs, Long e2eMs, String status,
                       String detail, boolean root, String user) { }

    public record Detail(String traceId, Instant startedAt, long durationMs, Long completeMs, List<Step> steps) { }

    public record StatRow(Instant bucket, String service, String kind, String operation, long count, double avgMs,
                          long maxMs, Double avgQueueMs, Double avgE2eMs, long errors) { }

    /** Műveletek (gyökérlépések = gateway kérések) időrendben, a hozzájuk tartozó összes lépés összesítésével. */
    public Page<Operation> operations(OperationFilter f, int page, int size) {
        MongoCollection<Document> steps = mongo.current().getCollection(STEPS);
        List<Bson> where = new ArrayList<>();
        where.add(eq("root", true));
        where.add(gte("startedAt", Date.from(f.from())));
        where.add(lt("startedAt", Date.from(f.to())));
        if (notBlank(f.q())) {
            where.add(regex("operation", Pattern.quote(f.q()), "i"));
        }
        if (notBlank(f.user())) {
            where.add(regex("user", Pattern.quote(f.user()), "i"));
        }
        if (f.minMs() != null) {
            where.add(gte("durationMs", f.minMs()));
        }
        if (f.errorsOnly()) {
            where.add(eq("status", "ERROR"));
        }
        Bson filter = and(where);
        long total = steps.countDocuments(filter);
        List<Document> roots = steps.find(filter).sort(descending("startedAt")).skip(page * size).limit(size)
                .into(new ArrayList<>());
        Map<String, Document> summary = summarize(steps, roots.stream().map(d -> d.getString("traceId")).toList());
        List<Operation> items = new ArrayList<>();
        for (Document r : roots) {
            Document s = summary.getOrDefault(r.getString("traceId"), new Document());
            Date start = r.getDate("startedAt");
            Date maxEnd = s.getDate("maxEnd");
            @SuppressWarnings("unchecked")
            List<String> services = (List<String>) s.getOrDefault("services", List.of());
            Map<String, Long> byKind = new LinkedHashMap<>();
            for (Object k : s.getList("kinds", Object.class, List.of())) {
                Document kd = (Document) k;
                byKind.merge(kd.getString("kind"), number(kd.get("ms")), Long::sum);
            }
            if (f.service() != null && !f.service().isBlank() && !services.contains(f.service())) {
                // a szolgáltatás szűrő a lépésekre vonatkozik (melyik szolgáltatást érintette a művelet)
                continue;
            }
            items.add(new Operation(r.getString("traceId"), start.toInstant(), r.getString("operation"),
                    r.getString("service"), r.getString("user"), number(r.get("durationMs")),
                    maxEnd == null ? null : maxEnd.getTime() - start.getTime(), r.getString("status"),
                    (int) number(s.get("steps")), services.stream().sorted().toList(), (int) number(s.get("errors")), byKind));
        }
        return new Page<>(items, total, page, size);
    }

    private Map<String, Document> summarize(MongoCollection<Document> steps, List<String> traceIds) {
        Map<String, Document> result = new LinkedHashMap<>();
        if (traceIds.isEmpty()) {
            return result;
        }
        List<Document> pipeline = List.of(
                new Document("$match", new Document("traceId", new Document("$in", traceIds))),
                new Document("$group", new Document("_id", new Document("traceId", "$traceId").append("kind", "$kind"))
                        .append("steps", new Document("$sum", 1))
                        .append("ms", new Document("$sum", "$durationMs"))
                        .append("maxEnd", new Document("$max", "$endedAt"))
                        .append("errors", new Document("$sum", new Document("$cond", List.of(new Document("$eq", List.of("$status", "ERROR")), 1, 0))))
                        .append("services", new Document("$addToSet", "$service"))),
                new Document("$group", new Document("_id", "$_id.traceId")
                        .append("steps", new Document("$sum", "$steps"))
                        .append("maxEnd", new Document("$max", "$maxEnd"))
                        .append("errors", new Document("$sum", "$errors"))
                        .append("services", new Document("$push", "$services"))
                        .append("kinds", new Document("$push", new Document("kind", "$_id.kind").append("ms", "$ms")))),
                new Document("$project", new Document("steps", 1).append("maxEnd", 1).append("errors", 1).append("kinds", 1)
                        .append("services", new Document("$reduce", new Document("input", "$services")
                                .append("initialValue", List.of())
                                .append("in", new Document("$setUnion", List.of("$$value", "$$this")))))));
        for (Document d : steps.aggregate(pipeline)) {
            result.put(d.getString("_id"), d);
        }
        return result;
    }

    /** Egy művelet összes lépése időrendben (vízesés nézethez), a művelet kezdetéhez viszonyított eltolással. */
    public Detail detail(String traceId) {
        List<Document> docs = mongo.current().getCollection(STEPS).find(eq("traceId", traceId))
                .sort(ascending("startedAt")).limit(2000).into(new ArrayList<>());
        if (docs.isEmpty()) {
            return null;
        }
        long t0 = docs.stream().mapToLong(d -> d.getDate("startedAt").getTime()).min().orElse(0);
        long maxEnd = docs.stream().mapToLong(d -> d.getDate("endedAt").getTime()).max().orElse(t0);
        Document root = docs.stream().filter(d -> Boolean.TRUE.equals(d.getBoolean("root"))).findFirst().orElse(docs.get(0));
        List<Step> steps = docs.stream().map(d -> new Step(d.getString("spanId"), d.getString("parentId"),
                d.getString("service"), d.getString("kind"), d.getString("operation"), d.getString("target"),
                d.getDate("startedAt").toInstant(), d.getDate("startedAt").getTime() - t0, number(d.get("durationMs")),
                nullableNumber(d.get("queueMs")), nullableNumber(d.get("e2eMs")), d.getString("status"),
                d.getString("detail"), Boolean.TRUE.equals(d.getBoolean("root")), d.getString("user"))).toList();
        return new Detail(traceId, Instant.ofEpochMilli(t0), number(root.get("durationMs")), maxEnd - t0, steps);
    }

    /** Átlagos időtartamok óránként vagy naponként (Europe/Budapest), műveletenként. */
    public List<StatRow> stats(String bucket, Instant from, Instant to, String service, String kind, String q, int limit) {
        String unit = "day".equals(bucket) ? "day" : "hour";
        Document match = new Document("hour", new Document("$gte", Date.from(from)).append("$lt", Date.from(to)));
        if (notBlank(service)) {
            match.append("service", service);
        }
        if (notBlank(kind)) {
            match.append("kind", kind);
        }
        if (notBlank(q)) {
            match.append("operation", new Document("$regex", Pattern.quote(q)).append("$options", "i"));
        }
        List<Document> pipeline = List.of(
                new Document("$match", match),
                new Document("$group", new Document("_id", new Document("bucket",
                        new Document("$dateTrunc", new Document("date", "$hour").append("unit", unit).append("timezone", ZONE)))
                        .append("service", "$service").append("kind", "$kind").append("operation", "$operation"))
                        .append("count", new Document("$sum", "$count"))
                        .append("sumMs", new Document("$sum", "$sumMs"))
                        .append("maxMs", new Document("$max", "$maxMs"))
                        .append("errors", new Document("$sum", "$errors"))
                        .append("queueCount", new Document("$sum", "$queueCount"))
                        .append("sumQueueMs", new Document("$sum", "$sumQueueMs"))
                        .append("e2eCount", new Document("$sum", "$e2eCount"))
                        .append("sumE2eMs", new Document("$sum", "$sumE2eMs"))),
                new Document("$sort", new Document("_id.bucket", -1).append("sumMs", -1)),
                new Document("$limit", Math.min(Math.max(limit, 1), 5000)));
        List<StatRow> rows = new ArrayList<>();
        for (Document d : mongo.current().getCollection(HOURLY).aggregate(pipeline)) {
            Document id = (Document) d.get("_id");
            long count = number(d.get("count"));
            long qc = number(d.get("queueCount"));
            long ec = number(d.get("e2eCount"));
            rows.add(new StatRow(id.getDate("bucket").toInstant(), id.getString("service"), id.getString("kind"),
                    id.getString("operation"), count, count == 0 ? 0 : round((double) number(d.get("sumMs")) / count),
                    number(d.get("maxMs")), qc == 0 ? null : round((double) number(d.get("sumQueueMs")) / qc),
                    ec == 0 ? null : round((double) number(d.get("sumE2eMs")) / ec), number(d.get("errors"))));
        }
        return rows;
    }

    /** A szűrőkhöz: az időszakban előforduló szolgáltatások és lépéstípusok. */
    public Map<String, List<String>> facets(Instant from, Instant to) {
        MongoCollection<Document> hourly = mongo.current().getCollection(HOURLY);
        Bson range = and(gte("hour", Date.from(from)), lt("hour", Date.from(to)));
        return Map.of(
                "services", hourly.distinct("service", range, String.class).into(new ArrayList<>()).stream().sorted().toList(),
                "kinds", hourly.distinct("kind", range, String.class).into(new ArrayList<>()).stream().sorted().toList());
    }

    static Instant orDefault(Instant value, Duration back) {
        return value != null ? value : Instant.now().minus(back);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static long number(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static Long nullableNumber(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
