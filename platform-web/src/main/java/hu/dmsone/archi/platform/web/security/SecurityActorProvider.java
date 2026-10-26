package hu.dmsone.archi.platform.web.security;

import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.outbox.ActorProvider;
import hu.dmsone.archi.platform.security.EffectivePermissions;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.web.permission.PermissionService;

/** Az eseményekbe a bejelentkezett felhasználót / API klienst teszi végrehajtóként. */
public class SecurityActorProvider implements ActorProvider {

    private final PermissionService permissionService;
    private final String serviceId;

    public SecurityActorProvider(PermissionService permissionService, String serviceId) {
        this.permissionService = permissionService;
        this.serviceId = serviceId;
    }

    @Override
    public EventEnvelope.Actor current() {
        if (!CurrentUser.isAuthenticated()) {
            return EventEnvelope.Actor.system(serviceId);
        }
        String type = CurrentUser.isServiceAccount() ? "API_CLIENT" : "USER";
        String id = CurrentUser.subject();
        if (TenantContext.get() != null) {
            EffectivePermissions p = permissionService.current();
            if (p.userId() != null) {
                id = p.userId();
            }
        }
        String name = CurrentUser.isServiceAccount() ? CurrentUser.clientId() : CurrentUser.displayName();
        return new EventEnvelope.Actor(id, name, type);
    }
}
