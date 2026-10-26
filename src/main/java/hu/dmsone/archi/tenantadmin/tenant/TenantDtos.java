package hu.dmsone.archi.tenantadmin.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.Set;

public final class TenantDtos {

    public record CreateTenantRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9]{1,29}$",
                    message = "kisbetű és szám, betűvel kezdődik, 2-30 karakter") String code,
            @NotBlank @Size(max = 200) String name,
            Set<String> enabledServices,
            Map<String, String> settings,
            @NotBlank @Size(max = 100) String adminUsername,
            @NotBlank @Email String adminEmail,
            @NotBlank @Size(max = 200) String adminName) { }

    public record UpdateTenantRequest(@NotBlank @Size(max = 200) String name, Map<String, String> settings) { }

    public record ServicesRequest(Set<String> services) { }

    public record BootstrapPassword(String username, String password) { }

    private TenantDtos() { }
}
