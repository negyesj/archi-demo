package hu.dmsone.archi.tenantadmin.adminuser;

import hu.dmsone.archi.platform.keycloak.KeycloakAdminClient;
import hu.dmsone.archi.platform.util.Passwords;
import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.tenantadmin.audit.AdminAuditPublisher;
import hu.dmsone.archi.tenantadmin.config.AdminProperties;
import hu.dmsone.archi.tenantadmin.security.AdminGuard;
import hu.dmsone.archi.tenantadmin.security.AdminUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A tenant admin felületre belépő üzemeltetők kezelése (Keycloak {@code platform} realm). */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final String ROLES = "PLATFORM_ADMIN|SUPPORT";

    private final AdminUserRepository repository;
    private final KeycloakAdminClient keycloak;
    private final AdminAuditPublisher audit;
    private final AdminProperties props;

    public AdminUserService(AdminUserRepository repository, KeycloakAdminClient keycloak,
                            AdminAuditPublisher audit, AdminProperties props) {
        this.repository = repository;
        this.keycloak = keycloak;
        this.audit = audit;
        this.props = props;
    }

    public record CreateRequest(@NotBlank String username, @NotBlank @Email String email, @NotBlank String fullName,
                                @NotBlank @Pattern(regexp = ROLES) String role) { }

    public record UpdateRequest(@NotBlank @Email String email, @NotBlank String fullName,
                                @NotBlank @Pattern(regexp = ROLES) String role, boolean active) { }

    public record Created(AdminUser user, String temporaryPassword) { }

    /** Az első platform admin rekordja (a Keycloak platform realmben importált felhasználóhoz). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBootstrapAdmin() {
        AdminProperties.BootstrapAdmin b = props.bootstrapAdmin();
        if (b == null || b.username() == null || repository.findByUsername(b.username()).isPresent()) {
            return;
        }
        repository.insert(new AdminUser(UUID.randomUUID(), b.username(), b.email(), b.fullName(),
                AdminUser.PLATFORM_ADMIN, true, null, Instant.now(), Instant.now()));
        log.info("Bootstrap platform admin létrehozva: {}", b.username());
    }

    public List<AdminUser> list() {
        return repository.findAll();
    }

    @Transactional
    public Created create(CreateRequest req) {
        if (repository.findByUsername(req.username()).isPresent()) {
            throw ApiException.conflict("Már létezik ilyen felhasználónév");
        }
        String password = Passwords.generate(12);
        String kid = keycloak.createUser(AdminGuard.PLATFORM_REALM, req.username(), req.email(), req.fullName(),
                password, true);
        AdminUser u = new AdminUser(UUID.randomUUID(), req.username(), req.email(), req.fullName(), req.role(),
                true, kid, Instant.now(), Instant.now());
        repository.insert(u);
        audit.record("ADMIN_USER_CREATE", "ADMIN_USER", req.username(), Map.of("role", req.role()));
        return new Created(u, password);
    }

    @Transactional
    public AdminUser update(UUID id, UpdateRequest req) {
        AdminUser u = repository.find(id).orElseThrow(() -> ApiException.notFound("Admin felhasználó"));
        repository.update(id, req.email(), req.fullName(), req.role(), req.active());
        String kid = u.keycloakId() != null ? u.keycloakId()
                : keycloak.findUserId(AdminGuard.PLATFORM_REALM, u.username()).orElse(null);
        if (kid != null) {
            keycloak.updateUser(AdminGuard.PLATFORM_REALM, kid, req.email(), req.fullName(), req.active());
        }
        audit.record("ADMIN_USER_UPDATE", "ADMIN_USER", u.username(),
                Map.of("role", req.role(), "active", req.active()));
        return repository.find(id).orElseThrow();
    }
}
