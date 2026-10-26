package hu.dmsone.archi.platform.security;

import java.util.Set;

/**
 * Egy felhasználó (vagy API kliens) effektív jogosultságai egy tenantban: saját és csoporton keresztüli
 * jogosultsági elemek, valamint a szervezeti egységei (adatszintű láthatósághoz).
 */
public record EffectivePermissions(
        String userId,
        String subject,
        String displayName,
        Set<String> permissions,
        Set<String> orgUnits,
        Set<String> groups) {

    public EffectivePermissions {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        orgUnits = orgUnits == null ? Set.of() : Set.copyOf(orgUnits);
        groups = groups == null ? Set.of() : Set.copyOf(groups);
    }

    public static EffectivePermissions none(String subject) {
        return new EffectivePermissions(null, subject, null, Set.of(), Set.of(), Set.of());
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasAny(String... codes) {
        for (String c : codes) {
            if (permissions.contains(c)) {
                return true;
            }
        }
        return false;
    }
}
