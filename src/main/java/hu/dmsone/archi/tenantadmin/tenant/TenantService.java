package hu.dmsone.archi.tenantadmin.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.db.SecretProvider;
import hu.dmsone.archi.platform.kafka.Topics;
import hu.dmsone.archi.platform.tenant.TenantStatus;
import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.tenantadmin.audit.AdminAuditPublisher;
import hu.dmsone.archi.tenantadmin.catalog.CatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tenant életciklus: létrehozás (aszinkron provisioning), konfiguráció, felfüggesztés, archiválás. */
@Service
public class TenantService {

    private final TenantRepository repository;
    private final TenantPublisher publisher;
    private final CatalogService catalog;
    private final AdminAuditPublisher audit;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final SecretProvider secrets;

    public TenantService(TenantRepository repository, TenantPublisher publisher, CatalogService catalog,
                         AdminAuditPublisher audit, KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
                         SecretProvider secrets) {
        this.repository = repository;
        this.publisher = publisher;
        this.catalog = catalog;
        this.audit = audit;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.secrets = secrets;
    }

    public List<TenantRecord> list() {
        return repository.findAll();
    }

    public TenantRecord get(String code) {
        return repository.find(code).orElseThrow(() -> ApiException.notFound("Tenant"));
    }

    @Transactional
    public TenantRecord create(TenantDtos.CreateTenantRequest req) {
        if (repository.exists(req.code())) {
            throw ApiException.conflict("Már létezik tenant ezzel a kóddal: " + req.code());
        }
        Set<String> services = req.enabledServices() == null ? Set.of() : req.enabledServices();
        catalog.validate(services);
        TenantRecord t = new TenantRecord(req.code(), req.name(), TenantStatus.PROVISIONING,
                req.settings() == null ? Map.of() : req.settings(), services, req.adminUsername(),
                req.adminEmail(), req.adminName(), null, null, 1, null, null);
        repository.insert(t);
        publisher.publishAfterCommit(t.code());
        sendProvisionCommandAfterCommit(t);
        audit.record("TENANT_CREATE", "TENANT", t.code(), Map.of("name", t.name(), "services", services));
        return get(t.code());
    }

    @Transactional
    public TenantRecord update(String code, TenantDtos.UpdateTenantRequest req) {
        TenantRecord before = get(code);
        repository.update(code, req.name(), req.settings() == null ? before.settings() : req.settings());
        publisher.publishAfterCommit(code);
        audit.record("TENANT_UPDATE", "TENANT", code, Map.of("name", req.name(),
                "settings", req.settings() == null ? Map.of() : req.settings()));
        return get(code);
    }

    @Transactional
    public TenantRecord setServices(String code, Set<String> services) {
        TenantRecord before = get(code);
        Set<String> target = services == null ? Set.of() : new HashSet<>(services);
        catalog.validate(target);
        repository.replaceServices(code, target);
        publisher.publishAfterCommit(code);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before.enabledServices());
        details.put("after", target);
        audit.record("TENANT_SERVICES", "TENANT", code, details);
        return get(code);
    }

    @Transactional
    public TenantRecord changeStatus(String code, TenantStatus target) {
        TenantRecord t = get(code);
        boolean allowed = switch (target) {
            case SUSPENDED -> t.status() == TenantStatus.ACTIVE;
            case ACTIVE -> t.status() == TenantStatus.SUSPENDED;
            case ARCHIVED -> t.status() == TenantStatus.SUSPENDED || t.status() == TenantStatus.FAILED;
            default -> false;
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.CONFLICT, "Nem engedélyezett állapotváltás",
                    t.status() + " → " + target + " nem lehetséges");
        }
        repository.updateStatus(code, target, null);
        publisher.publishAfterCommit(code);
        audit.record("TENANT_STATUS", "TENANT", code, Map.of("from", t.status(), "to", target));
        return get(code);
    }

    @Transactional
    public TenantRecord retryProvisioning(String code) {
        TenantRecord t = get(code);
        if (t.status() != TenantStatus.FAILED && t.status() != TenantStatus.PROVISIONING) {
            throw ApiException.conflict("Csak hibás vagy folyamatban lévő tenant provisioningja indítható újra");
        }
        repository.updateStatus(code, TenantStatus.PROVISIONING, null);
        publisher.publishAfterCommit(code);
        sendProvisionCommandAfterCommit(t);
        audit.record("TENANT_PROVISION_RETRY", "TENANT", code, null);
        return get(code);
    }

    /** Az első ügyfél-admin ideiglenes jelszava, egyszer olvasható ki (utána törlődik). */
    public TenantDtos.BootstrapPassword takeBootstrapPassword(String code) {
        TenantRecord t = get(code);
        String key = "tenant/" + code + "/bootstrap-password";
        String password = secrets.get(key).orElseThrow(() -> new ApiException(HttpStatus.GONE,
                "Már nem elérhető", "Az ideiglenes jelszót már kiolvasták, vagy még nem készült el."));
        secrets.delete(key);
        audit.record("TENANT_BOOTSTRAP_PASSWORD_READ", "TENANT", code, null);
        return new TenantDtos.BootstrapPassword(t.adminUsername(), password);
    }

    /** A provisioner visszajelzése (Kafka). */
    @Transactional
    public void onProvisioned(String code, String adminKeycloakId) {
        repository.markProvisioned(code, adminKeycloakId);
        publisher.publishAfterCommit(code);
        audit.record("TENANT_PROVISIONED", "TENANT", code, null);
    }

    @Transactional
    public void onProvisioningFailed(String code, String error) {
        repository.updateStatus(code, TenantStatus.FAILED, error);
        publisher.publishAfterCommit(code);
        audit.record("TENANT_PROVISION_FAILED", "TENANT", code, Map.of("error", error == null ? "" : error));
    }

    private void sendProvisionCommandAfterCommit(TenantRecord t) {
        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("type", "PROVISION");
        cmd.put("code", t.code());
        cmd.put("name", t.name());
        cmd.put("adminUsername", t.adminUsername());
        cmd.put("adminEmail", t.adminEmail());
        cmd.put("adminName", t.adminName());
        Runnable send = () -> {
            try {
                kafka.send(Topics.TENANT_COMMANDS, t.code(), objectMapper.writeValueAsString(cmd));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }
}
