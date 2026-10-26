package hu.dmsone.archi.provisioner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.db.SecretProvider;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.keycloak.KeycloakAdminClient;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantNames;
import hu.dmsone.archi.platform.util.Passwords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tenant provisioning, lépésenként idempotensen:
 * PostgreSQL → MongoDB → Keycloak realm → első ügyfél-admin → visszajelzés a tenant adminnak.
 * A szolgáltatások sémáit maguk a szolgáltatások migrálják, amikor a tenant aktív lesz.
 */
@Component
public class TenantProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningListener.class);

    private final PostgresProvisioner postgres;
    private final MongoProvisioner mongo;
    private final KeycloakAdminClient keycloak;
    private final SecretProvider secrets;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final ProvisionerProperties props;

    public TenantProvisioningListener(PostgresProvisioner postgres, MongoProvisioner mongo, KeycloakAdminClient keycloak,
                                      SecretProvider secrets, KafkaTemplate<String, String> kafka,
                                      ObjectMapper objectMapper, ProvisionerProperties props) {
        this.postgres = postgres;
        this.mongo = mongo;
        this.keycloak = keycloak;
        this.secrets = secrets;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @KafkaListener(topics = Topics.TENANT_COMMANDS, groupId = "provisioner")
    public void onCommand(ConsumerRecord<String, String> rec) throws Exception {
        JsonNode cmd = objectMapper.readTree(rec.value());
        if (!"PROVISION".equals(cmd.path("type").asText())) {
            return;
        }
        String code = TenantNames.requireValid(cmd.path("code").asText());
        TenantContext.set(code);
        try {
            log.info("Provisioning indul: {}", code);
            postgres.provision(code);
            mongo.provision(code);
            String realm = TenantNames.realm(code);
            keycloak.createTenantRealm(realm, cmd.path("name").asText(code), props.appRedirectUris(), props.appWebOrigins());
            String username = cmd.path("adminUsername").asText();
            String passwordKey = "tenant/" + code + "/bootstrap-password";
            String password = Passwords.generate(12);
            String adminId = keycloak.findUserId(realm, username).orElse(null);
            if (adminId == null) {
                adminId = keycloak.createUser(realm, username, cmd.path("adminEmail").asText(),
                        cmd.path("adminName").asText(), password, true);
                secrets.put(passwordKey, password);
            }
            send(Map.of("type", "PROVISIONED", "code", code, "adminKeycloakId", adminId));
            log.info("Provisioning kész: {}", code);
        } catch (Exception e) {
            log.error("Provisioning hiba: {}", code, e);
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("type", "FAILED");
            ev.put("code", code);
            ev.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            send(ev);
        } finally {
            TenantContext.clear();
        }
    }

    private void send(Map<String, ?> event) throws Exception {
        kafka.send(Topics.TENANT_EVENTS, String.valueOf(event.get("code")), objectMapper.writeValueAsString(event)).get();
    }
}
