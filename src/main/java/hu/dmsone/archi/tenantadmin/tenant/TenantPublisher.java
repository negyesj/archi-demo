package hu.dmsone.archi.tenantadmin.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.tenantadmin.config.AdminProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.Set;

/**
 * A tenant aktuális állapotát a compacted {@code platform.tenants} topicra publikálja (tranzakció után).
 * Ebből dolgozik a gateway, minden szolgáltatás és a workerek.
 */
@Component
public class TenantPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final TenantRepository repository;
    private final AdminProperties props;

    public TenantPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
                           TenantRepository repository, AdminProperties props) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.props = props;
    }

    public void publishAfterCommit(String code) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(code);
                }
            });
        } else {
            publish(code);
        }
    }

    public void publish(String code) {
        repository.find(code).ifPresent(t -> {
            Set<String> enabled = new HashSet<>(t.enabledServices());
            enabled.addAll(props.coreServices());
            TenantInfo.BootstrapAdmin admin = t.adminKeycloakId() == null ? null
                    : new TenantInfo.BootstrapAdmin(t.adminKeycloakId(), t.adminUsername(), t.adminEmail(), t.adminName());
            TenantInfo info = new TenantInfo(t.code(), t.name(), t.status(), enabled, t.settings(), admin, t.version());
            try {
                kafka.send(Topics.TENANTS, code, objectMapper.writeValueAsString(info));
            } catch (Exception e) {
                throw new IllegalStateException("Tenant publikálás sikertelen: " + code, e);
            }
        });
    }

    /** Induláskor újrapublikál mindent, hogy a topic biztosan konzisztens legyen az adatbázissal. */
    @EventListener(ApplicationReadyEvent.class)
    public void republishAll() {
        repository.findAll().forEach(t -> publish(t.code()));
    }
}
