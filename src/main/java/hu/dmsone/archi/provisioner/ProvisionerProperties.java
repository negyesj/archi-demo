package hu.dmsone.archi.provisioner;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param postgresAdminUrl  JDBC URL a {@code postgres} adatbázishoz adminisztrátori joggal (szerep és DB létrehozás)
 * @param mongoAdminUri     MongoDB URI root joggal (felhasználó létrehozás)
 * @param appRedirectUris   a felhasználói felület címei a Keycloak {@code app} klienséhez
 */
@ConfigurationProperties(prefix = "provisioner")
public record ProvisionerProperties(
        String postgresAdminUrl,
        String postgresAdminUser,
        String postgresAdminPassword,
        String mongoAdminUri,
        String keycloakUrl,
        String keycloakAdminUser,
        String keycloakAdminPassword,
        List<String> appRedirectUris,
        List<String> appWebOrigins) { }
