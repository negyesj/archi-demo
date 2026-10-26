package hu.dmsone.archi.platform.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Servlet szolgáltatások beállításai ({@code platform.web.*}). */
@ConfigurationProperties(prefix = "platform.web")
public class PlatformWebProperties {

    /** A Keycloak publikus címe, ahogy a token {@code iss} mezőjében szerepel (pl. http://localhost:8180). */
    private String issuerBase = "http://localhost:8180";
    /** A Keycloak belső címe a JWKS letöltéséhez (pl. http://keycloak:8080). Üres = issuerBase. */
    private String jwksBase;
    /** Az IAM szolgáltatás belső címe az effektív jogosultságok lekéréséhez. */
    private String iamBaseUrl = "http://iam:8080";
    /** Jogosultság cache élettartama Redisben. */
    private Duration permissionCacheTtl = Duration.ofMinutes(10);
    /** Alapszolgáltatás: minden tenantnál elérhető, nem kell a tenant konfigurációban engedélyezni. */
    private boolean coreService = false;

    public String getIssuerBase() { return issuerBase; }
    public void setIssuerBase(String issuerBase) { this.issuerBase = issuerBase; }
    public String getJwksBase() { return jwksBase == null || jwksBase.isBlank() ? issuerBase : jwksBase; }
    public void setJwksBase(String jwksBase) { this.jwksBase = jwksBase; }
    public String getIamBaseUrl() { return iamBaseUrl; }
    public void setIamBaseUrl(String iamBaseUrl) { this.iamBaseUrl = iamBaseUrl; }
    public Duration getPermissionCacheTtl() { return permissionCacheTtl; }
    public void setPermissionCacheTtl(Duration permissionCacheTtl) { this.permissionCacheTtl = permissionCacheTtl; }
    public boolean isCoreService() { return coreService; }
    public void setCoreService(boolean coreService) { this.coreService = coreService; }
}
