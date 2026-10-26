package hu.dmsone.archi.tenantadmin.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.kafka.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** A provisioner eredményei: PROVISIONED → ACTIVE, FAILED → FAILED (hibaüzenettel, újrapróbálható). */
@Component
public class ProvisioningEventsListener {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningEventsListener.class);

    private final TenantService tenants;
    private final ObjectMapper objectMapper;

    public ProvisioningEventsListener(TenantService tenants, ObjectMapper objectMapper) {
        this.tenants = tenants;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.TENANT_EVENTS, groupId = "tenant-admin-provisioning")
    public void onEvent(ConsumerRecord<String, String> rec) throws Exception {
        JsonNode e = objectMapper.readTree(rec.value());
        String code = e.path("code").asText();
        switch (e.path("type").asText()) {
            case "PROVISIONED" -> tenants.onProvisioned(code, e.path("adminKeycloakId").asText(null));
            case "FAILED" -> tenants.onProvisioningFailed(code, e.path("error").asText(null));
            default -> log.warn("Ismeretlen provisioning esemény: {}", e.path("type").asText());
        }
    }
}
