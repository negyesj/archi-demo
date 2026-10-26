package hu.dmsone.archi.iam.common;

import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.outbox.OutboxWriter;
import hu.dmsone.archi.platform.tenant.TenantContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/**
 * Minden IAM változás: esemény az outboxba ({@code iam.events}, audit és riport innen kapja), és ha a
 * jogosultságokat érinti, a tenant jogosultság-cache törlése a tranzakció után (azonnali hatás).
 */
@Component
public class IamChanges {

    private final OutboxWriter outbox;
    private final StringRedisTemplate redis;

    public IamChanges(OutboxWriter outbox, StringRedisTemplate redis) {
        this.outbox = outbox;
        this.redis = redis;
    }

    public void record(String entity, String id, String action, long version, Object before, Object after,
                       boolean affectsPermissions) {
        outbox.record(entity, id, action, version, before, after, EventEnvelope.Acl.forEveryone());
        if (affectsPermissions) {
            invalidatePermissionsAfterCommit();
        }
    }

    public void invalidatePermissionsAfterCommit() {
        String tenant = TenantContext.require();
        Runnable evict = () -> {
            Set<String> keys = redis.keys("perm:" + tenant + ":*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
