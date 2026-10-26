package hu.dmsone.archi.iam.permission;

import hu.dmsone.archi.iam.common.IamChanges;
import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.platform.web.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Jogosultsági elem hozzárendelése felhasználóhoz vagy csoporthoz, és a hozzárendelés törlése. */
@Service
public class GrantService {

    public record GrantRequest(@NotBlank String permissionCode,
                               @NotBlank @Pattern(regexp = "USER|GROUP") String subjectType,
                               @NotNull UUID subjectId) { }

    private final PermissionRepository repository;
    private final IamChanges changes;

    public GrantService(PermissionRepository repository, IamChanges changes) {
        this.repository = repository;
        this.changes = changes;
    }

    public List<PermissionRepository.Permission> permissions() {
        return repository.listActive();
    }

    public List<PermissionRepository.Grant> grants(String subjectType, UUID subjectId, String permissionCode) {
        return repository.grants(subjectType, subjectId, permissionCode);
    }

    @Transactional
    public PermissionRepository.Grant grant(GrantRequest req) {
        if (!repository.permissionActive(req.permissionCode())) {
            throw ApiException.badRequest("A jogosultsági elem nem elérhető ennél a tenantnál: " + req.permissionCode());
        }
        UUID id = UUID.randomUUID();
        if (repository.insertGrant(id, req.permissionCode(), req.subjectType(), req.subjectId(), CurrentUser.displayName()) == 0) {
            throw ApiException.conflict("Ez a hozzárendelés már létezik");
        }
        PermissionRepository.Grant g = repository.grants(req.subjectType(), req.subjectId(), req.permissionCode()).get(0);
        changes.record("PermissionGrant", id.toString(), "GRANT", System.currentTimeMillis(), null, g, true);
        return g;
    }

    @Transactional
    public void revoke(UUID id) {
        PermissionRepository.Grant before = repository.grants(null, null, null).stream()
                .filter(g -> g.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Hozzárendelés"));
        repository.deleteGrant(id);
        changes.record("PermissionGrant", id.toString(), "REVOKE", System.currentTimeMillis(), before, null, true);
    }
}
