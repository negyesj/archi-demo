package hu.dmsone.archi.tenantadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** A tenant admin saját beállításai ({@code platform.*}, a közös kulcsok mellett). */
@ConfigurationProperties(prefix = "platform")
public record AdminProperties(
        List<String> coreServices,
        BootstrapAdmin bootstrapAdmin,
        Kafka kafka,
        Keycloak keycloak) {

    public AdminProperties {
        coreServices = coreServices == null ? List.of() : coreServices;
        kafka = kafka == null ? new Kafka(1) : kafka;
    }

    public record BootstrapAdmin(String username, String email, String fullName) { }

    public record Kafka(int replicas) { }

    public record Keycloak(String url, String adminUser, String adminPassword) { }
}
