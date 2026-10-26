package hu.dmsone.archi.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keresés az audit eseményekben (időszak, szolgáltatás, entitás, végrehajtó, művelet, szabad szöveg). */
@Component
public class AuditQuery {

    public record AuditEvent(long id, String eventId, Instant occurredAt, String service, String entity,
                             String entityId, String action, Long version, String actorId, String actorName,
                             String actorType, String traceId, JsonNode before, JsonNode after, JsonNode changes) { }

    public record Filter(Instant from, Instant to, String service, String entity, String entityId, String actor,
                         String action, String q) { }

    public record Page(List<AuditEvent> items, long total, int page, int size) { }

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AuditQuery(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Page search(Filter f, int page, int size) {
        int s = Math.min(Math.max(size, 1), 500);
        int p = Math.max(page, 0);
        StringBuilder where = new StringBuilder(" where 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (f.from() != null) { where.append(" and occurred_at >= :from"); params.put("from", Timestamp.from(f.from())); }
        if (f.to() != null) { where.append(" and occurred_at < :to"); params.put("to", Timestamp.from(f.to())); }
        if (notBlank(f.service())) { where.append(" and service = :service"); params.put("service", f.service()); }
        if (notBlank(f.entity())) { where.append(" and entity = :entity"); params.put("entity", f.entity()); }
        if (notBlank(f.entityId())) { where.append(" and entity_id = :entityId"); params.put("entityId", f.entityId()); }
        if (notBlank(f.actor())) {
            where.append(" and (actor_id = :actor or lower(actor_name) like :actorLike)");
            params.put("actor", f.actor());
            params.put("actorLike", "%" + f.actor().toLowerCase() + "%");
        }
        if (notBlank(f.action())) { where.append(" and action = :action"); params.put("action", f.action()); }
        if (notBlank(f.q())) {
            where.append(" and (lower(coalesce(after::text, before::text)) like :q or entity_id = :qExact)");
            params.put("q", "%" + f.q().toLowerCase() + "%");
            params.put("qExact", f.q());
        }
        long total = jdbc.sql("select count(*) from audit.audit_event" + where).params(params).query(Long.class).single();
        Map<String, Object> pageParams = new LinkedHashMap<>(params);
        pageParams.put("limit", s);
        pageParams.put("offset", p * s);
        List<AuditEvent> items = jdbc.sql("select * from audit.audit_event" + where
                        + " order by occurred_at desc, id desc limit :limit offset :offset")
                .params(pageParams).query(this::map).list();
        return new Page(items, total, p, s);
    }

    private AuditEvent map(ResultSet rs, int i) throws SQLException {
        return new AuditEvent(rs.getLong("id"), rs.getString("event_id"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("service"), rs.getString("entity"), rs.getString("entity_id"), rs.getString("action"),
                rs.getObject("version") == null ? null : rs.getLong("version"),
                rs.getString("actor_id"), rs.getString("actor_name"), rs.getString("actor_type"), rs.getString("trace_id"),
                json(rs.getString("before")), json(rs.getString("after")), json(rs.getString("changes")));
    }

    private JsonNode json(String s) {
        if (s == null) {
            return null;
        }
        try {
            return objectMapper.readTree(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
