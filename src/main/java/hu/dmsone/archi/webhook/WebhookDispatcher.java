package hu.dmsone.archi.webhook;

import hu.dmsone.archi.platform.db.SecretProvider;
import hu.dmsone.archi.platform.db.TenantMigrator;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Kézbesítés: HTTP POST a feliratkozás URL-jére, HMAC-SHA256 aláírással
 * ({@code X-Webhook-Signature: sha256=<hex(HMAC(secret, timestamp + "." + body))>}).
 * Hibánál exponenciális visszaállással újrapróbál, a maximális próbálkozás után DEAD (kézzel újraküldhető).
 * Tartós hiba esetén a feliratkozás felfüggesztődik.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private record Due(UUID id, UUID subscriptionId, String url, String eventType, String payload, int attempts) { }

    private final TenantRegistry registry;
    private final TenantMigrator migrator;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final SecretProvider secrets;
    private final WebhookProperties props;
    private final RestClient http;

    public WebhookDispatcher(TenantRegistry registry, TenantMigrator migrator, JdbcClient jdbc, TransactionTemplate tx,
                             SecretProvider secrets, WebhookProperties props, RestClient.Builder builder) {
        this.registry = registry;
        this.migrator = migrator;
        this.jdbc = jdbc;
        this.tx = tx;
        this.secrets = secrets;
        this.props = props;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder.clone().requestFactory(rf).build();
    }

    @Scheduled(fixedDelayString = "${webhook.poll-interval-ms:2000}")
    public void dispatch() {
        for (TenantInfo t : registry.active()) {
            if (!migrator.isMigrated(t.code())) {
                continue;
            }
            try {
                TenantContext.runAs(t.code(), this::dispatchTenant);
            } catch (Exception e) {
                log.error("Webhook kézbesítési hiba, tenant: {}", t.code(), e);
            }
        }
    }

    private void dispatchTenant() {
        tx.executeWithoutResult(status -> {
            List<Due> due = jdbc.sql("""
                    select d.id, d.subscription_id, s.url, d.event_type, d.payload::text as payload, d.attempts
                    from webhook.delivery d join webhook.subscription s on s.id = d.subscription_id
                    where d.status = 'PENDING' and d.next_attempt_at <= now() and s.active and not s.suspended
                    order by d.next_attempt_at limit 50 for update of d skip locked
                    """).query((rs, i) -> new Due(rs.getObject("id", UUID.class), rs.getObject("subscription_id", UUID.class),
                    rs.getString("url"), rs.getString("event_type"), rs.getString("payload"), rs.getInt("attempts"))).list();
            for (Due d : due) {
                deliver(d);
            }
        });
    }

    private void deliver(Due d) {
        String tenant = TenantContext.require();
        String secret = secrets.get(SubscriptionService.secretKey(tenant, d.subscriptionId())).orElse("");
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        Integer statusCode = null;
        String error = null;
        try {
            var response = http.post().uri(d.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Webhook-Id", d.id().toString())
                    .header("X-Webhook-Event", d.eventType())
                    .header("X-Webhook-Timestamp", timestamp)
                    .header("X-Webhook-Signature", "sha256=" + hmac(secret, timestamp + "." + d.payload()))
                    .header(HttpHeaders.USER_AGENT, "archi-webhook/1")
                    .body(d.payload())
                    .retrieve().toBodilessEntity();
            statusCode = response.getStatusCode().value();
        } catch (RestClientResponseException e) {
            statusCode = e.getStatusCode().value();
            error = "HTTP " + statusCode;
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        if (error == null) {
            jdbc.sql("""
                    update webhook.delivery set status = 'SUCCEEDED', attempts = attempts + 1, last_status = :s,
                        last_error = null, delivered_at = now() where id = :id
                    """).param("s", statusCode).param("id", d.id()).update();
            jdbc.sql("update webhook.subscription set consecutive_failures = 0 where id = :id")
                    .param("id", d.subscriptionId()).update();
            return;
        }
        int attempts = d.attempts() + 1;
        boolean dead = attempts >= props.maxAttempts();
        long backoff = Math.min(30L * (1L << Math.min(attempts, 10)), 6 * 3600L);
        jdbc.sql("""
                update webhook.delivery set status = :st, attempts = :a, last_status = :s, last_error = :err,
                    next_attempt_at = now() + make_interval(secs => :backoff) where id = :id
                """).param("st", dead ? "DEAD" : "PENDING").param("a", attempts).param("s", statusCode)
                .param("err", error).param("backoff", (double) backoff).param("id", d.id()).update();
        jdbc.sql("""
                update webhook.subscription set consecutive_failures = consecutive_failures + 1,
                    suspended = (consecutive_failures + 1 >= :limit), updated_at = now() where id = :id
                """).param("limit", props.suspendAfterFailures()).param("id", d.subscriptionId()).update();
        log.warn("Webhook kézbesítés sikertelen ({}. próbálkozás): {} {}", attempts, d.eventType(), error);
    }

    static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
