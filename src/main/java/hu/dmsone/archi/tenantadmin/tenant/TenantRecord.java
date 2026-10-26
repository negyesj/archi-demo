package hu.dmsone.archi.tenantadmin.tenant;

import hu.dmsone.archi.platform.tenant.TenantStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record TenantRecord(
        String code,
        String name,
        TenantStatus status,
        Map<String, String> settings,
        Set<String> enabledServices,
        String adminUsername,
        String adminEmail,
        String adminName,
        String adminKeycloakId,
        String lastError,
        long version,
        Instant createdAt,
        Instant updatedAt) { }
