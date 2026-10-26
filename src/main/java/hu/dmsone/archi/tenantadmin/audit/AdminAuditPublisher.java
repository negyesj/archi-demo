package hu.dmsone.archi.tenantadmin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.tenantadmin.security.CurrentAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/** Admin művelet naplózása aszinkron módon: a tranzakció sikeres lezárása után a {@code platform.audit} topicra. */
@Component
public class AdminAuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public AdminAuditPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    public void record(String action, String targetType, String targetId, Object details) {
        AdminAuditEvent event = new AdminAuditEvent(UUID.randomUUID(), Instant.now(), CurrentAdmin.username(),
                CurrentAdmin.displayName(), action, targetType, targetId,
                details == null ? null : objectMapper.valueToTree(details));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(AdminAuditEvent event) {
        try {
            kafka.send(Topics.PLATFORM_AUDIT, event.actorId(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Admin audit esemény küldése sikertelen: {}", event.action(), e);
        }
    }
}
