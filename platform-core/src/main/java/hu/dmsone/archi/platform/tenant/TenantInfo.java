package hu.dmsone.archi.platform.tenant;

import java.util.Map;
import java.util.Set;

/**
 * Egy tenant publikált állapota a {@code platform.tenants} topicon.
 *
 * @param bootstrapAdmin az első ügyfél-admin (a provisioner tölti ki), az IAM ebből hozza létre a felhasználót
 */
public record TenantInfo(
        String code,
        String name,
        TenantStatus status,
        Set<String> enabledServices,
        Map<String, String> settings,
        BootstrapAdmin bootstrapAdmin,
        long version) {

    public TenantInfo {
        enabledServices = enabledServices == null ? Set.of() : Set.copyOf(enabledServices);
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    public boolean serviceEnabled(String serviceId) {
        return enabledServices.contains(serviceId);
    }

    public String setting(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }

    public record BootstrapAdmin(String keycloakId, String username, String email, String fullName) { }
}
