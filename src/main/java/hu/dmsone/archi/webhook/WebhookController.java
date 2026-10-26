package hu.dmsone.archi.webhook;

import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Feliratkozások és kézbesítési napló. A belső (felület) és a publikus API ugyanazt a szerződést adja. */
@RestController
public class WebhookController {

    public record Delivery(UUID id, UUID subscriptionId, String eventType, String status, int attempts,
                           Instant nextAttemptAt, Integer lastStatus, String lastError, Instant createdAt,
                           Instant deliveredAt) { }

    private final SubscriptionService subscriptions;
    private final JdbcClient jdbc;

    public WebhookController(SubscriptionService subscriptions, JdbcClient jdbc) {
        this.subscriptions = subscriptions;
        this.jdbc = jdbc;
    }

    @GetMapping({"/api/t/{tenant}/webhook/subscriptions", "/public/v1/t/{tenant}/webhook/subscriptions"})
    @RequiresPermission("integration.manage")
    public List<SubscriptionService.Subscription> list() {
        return subscriptions.list();
    }

    @PostMapping({"/api/t/{tenant}/webhook/subscriptions", "/public/v1/t/{tenant}/webhook/subscriptions"})
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("integration.manage")
    public SubscriptionService.Created create(@Valid @RequestBody SubscriptionService.SaveRequest req) {
        return subscriptions.create(req);
    }

    @PutMapping("/api/t/{tenant}/webhook/subscriptions/{id}")
    @RequiresPermission("integration.manage")
    public SubscriptionService.Subscription update(@PathVariable UUID id, @Valid @RequestBody SubscriptionService.SaveRequest req) {
        return subscriptions.update(id, req);
    }

    @DeleteMapping({"/api/t/{tenant}/webhook/subscriptions/{id}", "/public/v1/t/{tenant}/webhook/subscriptions/{id}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("integration.manage")
    public void delete(@PathVariable UUID id) {
        subscriptions.delete(id);
    }

    @GetMapping("/api/t/{tenant}/webhook/deliveries")
    @RequiresPermission("integration.manage")
    public List<Delivery> deliveries(@RequestParam(required = false) UUID subscriptionId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "100") int limit) {
        StringBuilder sql = new StringBuilder("select * from webhook.delivery where 1=1");
        if (subscriptionId != null) { sql.append(" and subscription_id = :sid"); }
        if (status != null && !status.isBlank()) { sql.append(" and status = :status"); }
        sql.append(" order by created_at desc limit :limit");
        return jdbc.sql(sql.toString()).param("sid", subscriptionId).param("status", status)
                .param("limit", Math.min(Math.max(limit, 1), 500))
                .query((rs, i) -> new Delivery(rs.getObject("id", UUID.class), rs.getObject("subscription_id", UUID.class),
                        rs.getString("event_type"), rs.getString("status"), rs.getInt("attempts"),
                        rs.getTimestamp("next_attempt_at").toInstant(), (Integer) rs.getObject("last_status"),
                        rs.getString("last_error"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("delivered_at") == null ? null : rs.getTimestamp("delivered_at").toInstant()))
                .list();
    }

    /** Kézi újraküldés (DEAD vagy sikertelen kézbesítés). */
    @PostMapping("/api/t/{tenant}/webhook/deliveries/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresPermission("integration.manage")
    public void retry(@PathVariable UUID id) {
        int n = jdbc.sql("""
                update webhook.delivery set status = 'PENDING', attempts = 0, next_attempt_at = now() where id = :id
                """).param("id", id).update();
        if (n == 0) {
            throw ApiException.notFound("Kézbesítés");
        }
    }
}
