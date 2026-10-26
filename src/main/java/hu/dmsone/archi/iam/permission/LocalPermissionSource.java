package hu.dmsone.archi.iam.permission;

import hu.dmsone.archi.iam.user.UserRepository;
import hu.dmsone.archi.platform.security.EffectivePermissions;
import hu.dmsone.archi.platform.web.permission.PermissionSource;
import org.springframework.stereotype.Component;

/** Az IAM a saját adatbázisából számolja az effektív jogosultságot (nincs önmagára irányuló HTTP hívás). */
@Component
public class LocalPermissionSource implements PermissionSource {

    private final UserRepository users;
    private final PermissionRepository permissions;

    public LocalPermissionSource(UserRepository users, PermissionRepository permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    @Override
    public EffectivePermissions load(String tenant, String subject) {
        return users.findByKeycloakId(subject)
                .filter(u -> u.active())
                .map(u -> new EffectivePermissions(u.id().toString(), subject, u.fullName(),
                        permissions.effectivePermissions(u.id()), permissions.orgUnitIds(u.id()),
                        permissions.groupIds(u.id())))
                .orElse(EffectivePermissions.none(subject));
    }
}
