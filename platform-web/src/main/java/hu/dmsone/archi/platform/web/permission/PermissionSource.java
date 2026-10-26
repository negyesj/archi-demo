package hu.dmsone.archi.platform.web.permission;

import hu.dmsone.archi.platform.security.EffectivePermissions;

/** Az effektív jogosultságok forrása. Alapesetben távoli hívás az IAM-re; az IAM saját maga helyben számol. */
public interface PermissionSource {
    EffectivePermissions load(String tenant, String subject);
}
