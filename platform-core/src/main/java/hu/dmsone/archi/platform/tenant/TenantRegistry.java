package hu.dmsone.archi.platform.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tenantok aktuális állapotának helyi másolata. Minden példány saját consumer grouppal, az elejéről olvassa a
 * compacted {@code platform.tenants} topicot, így induláskor is teljes képe van.
 */
public class TenantRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistry.class);

    private final Map<String, TenantInfo> tenants = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher publisher;

    public TenantRegistry(ObjectMapper objectMapper, ApplicationEventPublisher publisher) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
    }

    @KafkaListener(
            id = "#{'platform-tenants-' + T(java.util.UUID).randomUUID().toString()}",
            topics = Topics.TENANTS,
            groupId = "${spring.application.name}-tenants-#{T(java.util.UUID).randomUUID().toString()}",
            properties = {"auto.offset.reset=earliest"})
    public void onMessage(ConsumerRecord<String, String> rec) {
        String code = rec.key();
        if (code == null) {
            return;
        }
        if (rec.value() == null) {
            tenants.remove(code);
            publisher.publishEvent(new TenantChangedEvent(code, null));
            return;
        }
        try {
            TenantInfo info = objectMapper.readValue(rec.value(), TenantInfo.class);
            TenantInfo previous = tenants.get(code);
            if (previous != null && previous.version() > info.version()) {
                return;
            }
            tenants.put(code, info);
            log.info("Tenant állapot frissült: {} ({})", code, info.status());
            publisher.publishEvent(new TenantChangedEvent(code, info));
        } catch (Exception e) {
            log.error("Hibás tenant üzenet, kulcs: {}", code, e);
        }
    }

    public Optional<TenantInfo> find(String code) {
        return Optional.ofNullable(code).map(tenants::get);
    }

    public Optional<TenantInfo> findActive(String code) {
        return find(code).filter(TenantInfo::isActive);
    }

    public Collection<TenantInfo> all() {
        return List.copyOf(tenants.values());
    }

    public List<TenantInfo> active() {
        return tenants.values().stream().filter(TenantInfo::isActive).toList();
    }
}
