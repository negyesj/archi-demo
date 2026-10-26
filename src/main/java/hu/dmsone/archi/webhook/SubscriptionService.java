package hu.dmsone.archi.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.db.SecretProvider;
import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.outbox.OutboxWriter;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.util.Passwords;
import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.platform.web.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Webhook feliratkozások: eseménytípusok (pl. {@code irat.kuldemeny.iktatas}, {@code *}) és cél URL. */
@Service
public class SubscriptionService {

    public record Subscription(UUID id, String name, String url, List<String> eventTypes, boolean active,
                               boolean suspended, int consecutiveFailures, String createdBy, Instant createdAt) { }

    public record SaveRequest(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 1000) String url,
                              @NotEmpty List<String> eventTypes, Boolean active) { }

    public record Created(Subscription subscription, String signingSecret) { }

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final SecretProvider secrets;
    private final OutboxWriter outbox;
    private final WebhookProperties props;

    public SubscriptionService(JdbcClient jdbc, ObjectMapper objectMapper, SecretProvider secrets, OutboxWriter outbox,
                               WebhookProperties props) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.secrets = secrets;
        this.outbox = outbox;
        this.props = props;
    }

    public static String secretKey(String tenant, UUID id) {
        return "tenant/" + tenant + "/webhook/" + id;
    }

    public List<Subscription> list() {
        return jdbc.sql("select * from webhook.subscription order by name").query(this::map).list();
    }

    public Subscription get(UUID id) {
        return jdbc.sql("select * from webhook.subscription where id = :id").param("id", id).query(this::map)
                .optional().orElseThrow(() -> ApiException.notFound("Feliratkozás"));
    }

    /** Aktív, nem felfüggesztett feliratkozások, amelyek az adott eseménytípust kérik. */
    public List<Subscription> matching(String eventType) {
        return list().stream()
                .filter(s -> s.active() && !s.suspended())
                .filter(s -> s.eventTypes().contains("*") || s.eventTypes().contains(eventType)
                        || s.eventTypes().stream().anyMatch(t -> t.endsWith(".*") && eventType.startsWith(t.substring(0, t.length() - 1))))
                .toList();
    }

    @Transactional
    public Created create(SaveRequest req) {
        validateUrl(req.url());
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into webhook.subscription (id, name, url, event_types, active, created_by)
                values (:id, :name, :url, cast(:types as jsonb), :active, :by)
                """).param("id", id).param("name", req.name()).param("url", req.url())
                .param("types", json(req.eventTypes())).param("active", req.active() == null || req.active())
                .param("by", CurrentUser.displayName()).update();
        String secret = Passwords.generate(32);
        secrets.put(secretKey(TenantContext.require(), id), secret);
        Subscription s = get(id);
        outbox.record("Subscription", id.toString(), "CREATE", System.currentTimeMillis(), null, s, EventEnvelope.Acl.forEveryone());
        return new Created(s, secret);
    }

    @Transactional
    public Subscription update(UUID id, SaveRequest req) {
        validateUrl(req.url());
        Subscription before = get(id);
        jdbc.sql("""
                update webhook.subscription set name = :name, url = :url, event_types = cast(:types as jsonb),
                    active = :active, suspended = false, consecutive_failures = 0, updated_at = now() where id = :id
                """).param("id", id).param("name", req.name()).param("url", req.url())
                .param("types", json(req.eventTypes())).param("active", req.active() == null || req.active()).update();
        Subscription after = get(id);
        outbox.record("Subscription", id.toString(), "UPDATE", System.currentTimeMillis(), before, after, EventEnvelope.Acl.forEveryone());
        return after;
    }

    @Transactional
    public void delete(UUID id) {
        Subscription before = get(id);
        jdbc.sql("delete from webhook.subscription where id = :id").param("id", id).update();
        secrets.delete(secretKey(TenantContext.require(), id));
        outbox.record("Subscription", id.toString(), "DELETE", System.currentTimeMillis(), before, null, EventEnvelope.Acl.forEveryone());
    }

    private void validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Érvénytelen URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"https".equals(scheme) && !(props.allowInsecureHttp() && "http".equals(scheme))) {
            throw ApiException.badRequest("A webhook cél URL-je csak https:// lehet");
        }
        if (uri.getHost() == null) {
            throw ApiException.badRequest("Az URL-ből hiányzik a host");
        }
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Subscription map(ResultSet rs, int i) throws SQLException {
        List<String> types;
        try {
            types = objectMapper.readValue(rs.getString("event_types"), new TypeReference<List<String>>() { });
        } catch (Exception e) {
            types = List.of();
        }
        return new Subscription(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("url"), types,
                rs.getBoolean("active"), rs.getBoolean("suspended"), rs.getInt("consecutive_failures"),
                rs.getString("created_by"), rs.getTimestamp("created_at").toInstant());
    }
}
