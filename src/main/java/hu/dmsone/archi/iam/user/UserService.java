package hu.dmsone.archi.iam.user;

import hu.dmsone.archi.iam.common.IamChanges;
import hu.dmsone.archi.iam.common.Page;
import hu.dmsone.archi.platform.keycloak.KeycloakAdminClient;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantNames;
import hu.dmsone.archi.platform.util.Passwords;
import hu.dmsone.archi.platform.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Felhasználók kezelése. A bejelentkezési fiók a tenant Keycloak realmjében jön létre, minden más adat
 * (törzsadat, csoport, szervezeti egység, jogosultság) itt, az IAM-ben van.
 */
@Service
public class UserService {

    public static final String HUMAN = "HUMAN";
    public static final String TECHNICAL = "TECHNICAL";

    private final UserRepository repository;
    private final KeycloakAdminClient keycloak;
    private final IamChanges changes;

    public UserService(UserRepository repository, KeycloakAdminClient keycloak, IamChanges changes) {
        this.repository = repository;
        this.keycloak = keycloak;
        this.changes = changes;
    }

    public Page<UserDtos.User> search(String q, Boolean active, String type, int page, int size) {
        int s = Math.min(Math.max(size, 1), 200);
        int p = Math.max(page, 0);
        return new Page<>(repository.search(q, active, type, s, p * s), repository.count(q, active, type), p, s);
    }

    public UserDtos.User get(UUID id) {
        return repository.find(id).orElseThrow(() -> ApiException.notFound("Felhasználó"));
    }

    public UserDtos.Memberships memberships(UUID id) {
        get(id);
        return new UserDtos.Memberships(repository.groupsOf(id), repository.orgUnitsOf(id));
    }

    @Transactional
    public UserDtos.CreatedUser create(UserDtos.CreateUser req) {
        if (repository.findByUsername(req.username()).isPresent()) {
            throw ApiException.conflict("Már létezik ilyen felhasználónév: " + req.username());
        }
        String realm = TenantNames.realm(TenantContext.require());
        String password = Passwords.generate(12);
        String kid = keycloak.createUser(realm, req.username(), req.email(), req.fullName(), password, true);
        UserDtos.User u = new UserDtos.User(UUID.randomUUID(), kid, req.username(), req.email(), req.fullName(),
                HUMAN, true, 0, Instant.now(), Instant.now());
        repository.insert(u);
        UserDtos.User saved = get(u.id());
        changes.record("User", saved.id().toString(), "CREATE", saved.version(), null, saved, false);
        return new UserDtos.CreatedUser(saved, password);
    }

    @Transactional
    public UserDtos.User update(UUID id, UserDtos.UpdateUser req) {
        UserDtos.User before = get(id);
        if (repository.update(id, req.email(), req.fullName(), req.active(), req.version()) == 0) {
            throw ApiException.conflict("A felhasználót közben más módosította, töltse újra.");
        }
        UserDtos.User after = get(id);
        syncKeycloak(after);
        changes.record("User", id.toString(), "UPDATE", after.version(), before, after, before.active() != after.active());
        return after;
    }

    /** Törlés = inaktiválás: az audit és az iratok hivatkoznak a felhasználóra. A Keycloak fiók letiltódik. */
    @Transactional
    public void delete(UUID id) {
        UserDtos.User before = get(id);
        repository.setActive(id, false);
        UserDtos.User after = get(id);
        syncKeycloak(after);
        changes.record("User", id.toString(), "DELETE", after.version(), before, after, true);
    }

    @Transactional
    public String resetPassword(UUID id) {
        UserDtos.User u = get(id);
        if (u.keycloakId() == null || !HUMAN.equals(u.type())) {
            throw ApiException.badRequest("Ennek a felhasználónak nincs jelszava");
        }
        String password = Passwords.generate(12);
        keycloak.resetPassword(TenantNames.realm(TenantContext.require()), u.keycloakId(), password, true);
        changes.record("User", id.toString(), "RESET_PASSWORD", u.version(), null, u, false);
        return password;
    }

    /** Az első ügyfél-admin felvétele (a provisioner által a Keycloakban létrehozott fiókhoz). */
    @Transactional
    public UserDtos.User ensureFromKeycloak(String keycloakId, String username, String email, String fullName) {
        return repository.findByKeycloakId(keycloakId).orElseGet(() -> {
            UserDtos.User u = new UserDtos.User(UUID.randomUUID(), keycloakId, username, email,
                    fullName == null ? username : fullName, HUMAN, true, 0, Instant.now(), Instant.now());
            repository.insert(u);
            changes.record("User", u.id().toString(), "CREATE", 0, null, u, true);
            return get(u.id());
        });
    }

    @Transactional
    public UserDtos.User createTechnical(String keycloakId, String username, String fullName) {
        UserDtos.User u = new UserDtos.User(UUID.randomUUID(), keycloakId, username, null, fullName, TECHNICAL,
                true, 0, Instant.now(), Instant.now());
        repository.insert(u);
        changes.record("User", u.id().toString(), "CREATE", 0, null, u, false);
        return get(u.id());
    }

    private void syncKeycloak(UserDtos.User u) {
        if (u.keycloakId() != null && HUMAN.equals(u.type())) {
            keycloak.updateUser(TenantNames.realm(TenantContext.require()), u.keycloakId(), u.email(), u.fullName(), u.active());
        }
    }
}
