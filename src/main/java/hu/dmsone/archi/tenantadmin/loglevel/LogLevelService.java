package hu.dmsone.archi.tenantadmin.loglevel;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.logging.LogLevelMessage;
import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.tenantadmin.audit.AdminAuditPublisher;
import hu.dmsone.archi.tenantadmin.security.CurrentAdmin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Központi loglevel: szolgáltatásonként, opcionálisan tenantra szűkítve, lejárati idővel.
 * A beállítás a compacted {@code platform.loglevel} topicon jut el minden példányhoz.
 */
@Service
public class LogLevelService {

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final AdminAuditPublisher audit;

    public LogLevelService(JdbcClient jdbc, KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
                           AdminAuditPublisher audit) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    public record SetRequest(@NotBlank String service, String logger,
                             @NotBlank @Pattern(regexp = "TRACE|DEBUG|INFO|WARN|ERROR") String level,
                             String tenant, @Min(1) @Max(1440) Integer minutes) { }

    public record Setting(String key, String service, String logger, String level, String tenant,
                          Instant expiresAt, String createdBy, Instant createdAt) { }

    public List<Setting> list() {
        return jdbc.sql("select * from loglevel_setting order by service, logger").query((rs, i) -> new Setting(
                rs.getString("key"), rs.getString("service"), rs.getString("logger"), rs.getString("level"),
                rs.getString("tenant"),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getString("created_by"), rs.getTimestamp("created_at").toInstant())).list();
    }

    public Setting set(SetRequest req) {
        String logger = req.logger() == null || req.logger().isBlank() ? "ROOT" : req.logger();
        String tenant = req.tenant() == null || req.tenant().isBlank() ? null : req.tenant();
        Instant expires = req.minutes() == null ? null : Instant.now().plus(Duration.ofMinutes(req.minutes()));
        LogLevelMessage msg = new LogLevelMessage(req.service(), logger, req.level(), tenant, expires);
        jdbc.sql("""
                insert into loglevel_setting (key, service, logger, level, tenant, expires_at, created_by)
                values (:key, :service, :logger, :level, :tenant, :expires, :by)
                on conflict (key) do update set level = excluded.level, expires_at = excluded.expires_at,
                    created_by = excluded.created_by, created_at = now()
                """)
                .param("key", msg.key()).param("service", req.service()).param("logger", logger)
                .param("level", req.level()).param("tenant", tenant)
                .param("expires", expires == null ? null : Timestamp.from(expires))
                .param("by", CurrentAdmin.username())
                .update();
        send(msg);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("level", req.level());
        details.put("tenant", tenant);
        details.put("expiresAt", expires);
        audit.record("LOGLEVEL_SET", "LOGLEVEL", msg.key(), details);
        return list().stream().filter(s -> s.key().equals(msg.key())).findFirst().orElseThrow();
    }

    public void reset(String key) {
        Setting s = list().stream().filter(x -> x.key().equals(key)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Loglevel beállítás"));
        jdbc.sql("delete from loglevel_setting where key = :key").param("key", key).update();
        send(new LogLevelMessage(s.service(), s.logger(), null, s.tenant(), null));
        audit.record("LOGLEVEL_RESET", "LOGLEVEL", key, null);
    }

    /** Lejárt beállítások visszaállítása alapértelmezettre. */
    @Scheduled(fixedDelay = 60_000)
    public void expire() {
        List<Setting> expired = list().stream()
                .filter(s -> s.expiresAt() != null && s.expiresAt().isBefore(Instant.now())).toList();
        for (Setting s : expired) {
            jdbc.sql("delete from loglevel_setting where key = :key").param("key", s.key()).update();
            send(new LogLevelMessage(s.service(), s.logger(), null, s.tenant(), null));
            audit.record("LOGLEVEL_EXPIRED", "LOGLEVEL", s.key(), null);
        }
    }

    private void send(LogLevelMessage msg) {
        try {
            kafka.send(Topics.LOGLEVEL, msg.key(), objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
