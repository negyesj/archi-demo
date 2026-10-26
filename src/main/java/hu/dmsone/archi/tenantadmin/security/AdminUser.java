package hu.dmsone.archi.tenantadmin.security;

import java.time.Instant;
import java.util.UUID;

public record AdminUser(UUID id, String username, String email, String fullName, String role, boolean active,
                        String keycloakId, Instant createdAt, Instant updatedAt) {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String SUPPORT = "SUPPORT";

    public boolean isPlatformAdmin() {
        return PLATFORM_ADMIN.equals(role);
    }
}
