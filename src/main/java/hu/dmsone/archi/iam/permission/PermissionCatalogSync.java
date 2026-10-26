package hu.dmsone.archi.iam.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.iam.common.IamChanges;
import hu.dmsone.archi.iam.group.GroupService;
import hu.dmsone.archi.iam.user.UserDtos;
import hu.dmsone.archi.iam.user.UserService;
import hu.dmsone.archi.platform.db.TenantMigratedEvent;
import hu.dmsone.archi.platform.db.TenantMigrator;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.tenant.TenantChangedEvent;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tenant {@code iam.permission} tábláját a szolgáltatás-manifestekből és a tenant konfigurációjából
 * szinkronizálja: csak az engedélyezett szolgáltatások jogosultsági elemei aktívak. A rendszer admin csoport
 * minden aktív elemet megkap. Az első ügyfél-admin (provisioner) itt kerül be a rendszer admin csoportba.
 */
@Component
public class PermissionCatalogSync {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogSync.class);
    public static final String ADMIN_GROUP = "Rendszeradminisztrátorok";

    private final Map<String, JsonNode> manifests = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final TenantRegistry registry;
    private final TenantMigrator migrator;
    private final PermissionRepository permissions;
    private final GroupService groups;
    private final UserService users;
    private final IamChanges changes;
    private final TransactionTemplate tx;

    public PermissionCatalogSync(ObjectMapper objectMapper, TenantRegistry registry, TenantMigrator migrator,
                                 PermissionRepository permissions, GroupService groups, UserService users,
                                 IamChanges changes, TransactionTemplate tx) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.migrator = migrator;
        this.permissions = permissions;
        this.groups = groups;
        this.users = users;
        this.changes = changes;
        this.tx = tx;
    }

    @KafkaListener(
            id = "#{'iam-catalog-' + T(java.util.UUID).randomUUID().toString()}",
            topics = Topics.CATALOG,
            groupId = "iam-catalog-#{T(java.util.UUID).randomUUID().toString()}",
            properties = {"auto.offset.reset=earliest"})
    public void onManifest(ConsumerRecord<String, String> rec) throws Exception {
        if (rec.value() == null) {
            return;
        }
        JsonNode m = objectMapper.readTree(rec.value());
        if (m.path("platform").asBoolean(false)) {
            return;
        }
        manifests.put(m.path("id").asText(), m);
        registry.active().stream().filter(t -> migrator.isMigrated(t.code())).forEach(this::sync);
    }

    @EventListener
    public void onMigrated(TenantMigratedEvent event) {
        sync(event.tenant());
    }

    @EventListener
    public void onTenantChanged(TenantChangedEvent event) {
        if (!event.removed() && event.tenant().isActive() && migrator.isMigrated(event.code())) {
            sync(event.tenant());
        }
    }

    public synchronized void sync(TenantInfo tenant) {
        TenantContext.runAs(tenant.code(), () -> {
            try {
                tx.executeWithoutResult(status -> {
                    Set<String> enabled = new HashSet<>(tenant.enabledServices());
                    for (JsonNode m : manifests.values()) {
                        String serviceId = m.path("id").asText();
                        boolean active = enabled.contains(serviceId);
                        for (JsonNode p : m.path("permissions")) {
                            permissions.upsert(p.path("code").asText(), serviceId, p.path("name").asText(),
                                    p.path("description").asText(null), active);
                        }
                    }
                    if (!manifests.isEmpty()) {
                        permissions.deactivateServicesExcept(enabled);
                    }
                    GroupService.Group admin = groups.findSystemAdminGroup();
                    if (admin == null) {
                        admin = groups.createInternal(ADMIN_GROUP, "Minden engedélyezett szolgáltatás minden joga", true);
                    }
                    permissions.grantAllActiveTo(admin.id());
                    if (tenant.bootstrapAdmin() != null && tenant.bootstrapAdmin().keycloakId() != null) {
                        TenantInfo.BootstrapAdmin b = tenant.bootstrapAdmin();
                        UserDtos.User u = users.ensureFromKeycloak(b.keycloakId(), b.username(), b.email(), b.fullName());
                        groups.addMember(admin.id(), u.id());
                    }
                    changes.invalidatePermissionsAfterCommit();
                });
                log.info("Jogosultsági katalógus szinkronizálva: {}", tenant.code());
            } catch (Exception e) {
                log.error("Jogosultsági katalógus szinkron hiba: {}", tenant.code(), e);
            }
        });
    }
}
