package hu.dmsone.archi.tenantadmin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin audit fogyasztó és kereső. Csak beszúrás (idempotens az eseményazonosítóra). */
@Component
public class AdminAuditStore {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditStore.class);

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AdminAuditStore(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.PLATFORM_AUDIT, groupId = "tenant-admin-audit")
    public void onEvent(ConsumerRecord<String, String> rec) throws Exception {
        AdminAuditEvent e = objectMapper.readValue(rec.value(), AdminAuditEvent.class);
        jdbc.sql("""
                insert into admin_audit (event_id, occurred_at, actor_id, actor_name, action, target_type, target_id, details)
                values (:eventId, :occurredAt, :actorId, :actorName, :action, :targetType, :targetId, cast(:details as jsonb))
                on conflict (event_id) do nothing
                """)
                .param("eventId", e.eventId())
                .param("occurredAt", Timestamp.from(e.occurredAt()))
                .param("actorId", e.actorId())
                .param("actorName", e.actorName())
                .param("action", e.action())
                .param("targetType", e.targetType())
                .param("targetId", e.targetId())
                .param("details", e.details() == null ? null : e.details().toString())
                .update();
        log.debug("Admin audit rögzítve: {} {}", e.action(), e.targetId());
    }

    public record AuditRow(long id, String eventId, Instant occurredAt, String actorId, String actorName,
                           String action, String targetType, String targetId, Object details) { }

    public List<AuditRow> search(Instant from, Instant to, String actor, String action, String targetType,
                                 String targetId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select * from admin_audit where 1=1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (from != null) { sql.append(" and occurred_at >= :from"); params.put("from", Timestamp.from(from)); }
        if (to != null) { sql.append(" and occurred_at < :to"); params.put("to", Timestamp.from(to)); }
        if (actor != null && !actor.isBlank()) { sql.append(" and actor_id = :actor"); params.put("actor", actor); }
        if (action != null && !action.isBlank()) { sql.append(" and action = :action"); params.put("action", action); }
        if (targetType != null && !targetType.isBlank()) { sql.append(" and target_type = :tt"); params.put("tt", targetType); }
        if (targetId != null && !targetId.isBlank()) { sql.append(" and target_id = :tid"); params.put("tid", targetId); }
        sql.append(" order by occurred_at desc limit :limit offset :offset");
        params.put("limit", Math.min(Math.max(limit, 1), 500));
        params.put("offset", Math.max(offset, 0));
        List<AuditRow> rows = new ArrayList<>();
        jdbc.sql(sql.toString()).params(params).query((rs, i) -> {
            Object details = null;
            String d = rs.getString("details");
            if (d != null) {
                try { details = objectMapper.readTree(d); } catch (Exception ignored) { details = d; }
            }
            return new AuditRow(rs.getLong("id"), rs.getString("event_id"), rs.getTimestamp("occurred_at").toInstant(),
                    rs.getString("actor_id"), rs.getString("actor_name"), rs.getString("action"),
                    rs.getString("target_type"), rs.getString("target_id"), details);
        }).list().forEach(rows::add);
        return rows;
    }
}
