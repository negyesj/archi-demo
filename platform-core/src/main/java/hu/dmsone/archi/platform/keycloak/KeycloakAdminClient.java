package hu.dmsone.archi.platform.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Minimális Keycloak Admin REST kliens (realm, felhasználó, szolgáltatásfiókos kliens).
 * Szándékosan nem a keycloak-admin-client könyvtár: kevesebb függőség, verziófüggetlen.
 * A Keycloak csak hitelesít; jogosultságot nem tárolunk benne.
 */
public class KeycloakAdminClient {

    private final RestClient http;
    private final String adminUser;
    private final String adminPassword;
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public KeycloakAdminClient(RestClient.Builder builder, String baseUrl, String adminUser, String adminPassword) {
        this.http = builder.clone().baseUrl(baseUrl).build();
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    public record ServiceClient(String id, String clientId, String secret, String serviceAccountUserId) { }

    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUser);
        form.add("password", adminPassword);
        JsonNode res = http.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        token = res.path("access_token").asText();
        tokenExpiry = Instant.now().plusSeconds(Math.max(10, res.path("expires_in").asLong(60) - 20));
        return token;
    }

    public boolean realmExists(String realm) {
        try {
            http.get().uri("/admin/realms/{realm}", realm).headers(h -> h.setBearerAuth(token()))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    /** Tenant realm létrehozása egy publikus, PKCE-s {@code app} klienssel a felhasználói felülethez. */
    public void createTenantRealm(String realm, String displayName, List<String> redirectUris, List<String> webOrigins) {
        if (realmExists(realm)) {
            return;
        }
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("clientId", "app");
        client.put("name", "Felhasználói felület");
        client.put("enabled", true);
        client.put("publicClient", true);
        client.put("standardFlowEnabled", true);
        client.put("directAccessGrantsEnabled", false);
        client.put("redirectUris", redirectUris);
        client.put("webOrigins", webOrigins);
        client.put("attributes", Map.of("pkce.code.challenge.method", "S256", "post.logout.redirect.uris", "+"));

        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("realm", realm);
        rep.put("displayName", displayName);
        rep.put("enabled", true);
        rep.put("sslRequired", "external");
        rep.put("registrationAllowed", false);
        rep.put("resetPasswordAllowed", false);
        rep.put("bruteForceProtected", true);
        rep.put("clients", List.of(client));
        http.post().uri("/admin/realms").headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(rep).retrieve().toBodilessEntity();
    }

    public void deleteRealm(String realm) {
        http.delete().uri("/admin/realms/{realm}", realm).headers(h -> h.setBearerAuth(token()))
                .retrieve().toBodilessEntity();
    }

    public Optional<String> findUserId(String realm, String username) {
        JsonNode users = http.get()
                .uri(b -> b.path("/admin/realms/{realm}/users").queryParam("username", username)
                        .queryParam("exact", "true").build(realm))
                .headers(h -> h.setBearerAuth(token()))
                .retrieve().body(JsonNode.class);
        if (users != null && users.isArray() && !users.isEmpty()) {
            return Optional.of(users.get(0).path("id").asText());
        }
        return Optional.empty();
    }

    /** Felhasználó létrehozása; ha már létezik, a meglévő azonosítóját adja vissza. */
    public String createUser(String realm, String username, String email, String fullName,
                             String password, boolean temporaryPassword) {
        String[] names = splitName(fullName);
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("username", username);
        rep.put("email", email);
        rep.put("firstName", names[0]);
        rep.put("lastName", names[1]);
        rep.put("enabled", true);
        rep.put("emailVerified", true);
        if (password != null) {
            rep.put("credentials", List.of(Map.of("type", "password", "value", password, "temporary", temporaryPassword)));
        }
        try {
            ResponseEntity<Void> res = http.post().uri("/admin/realms/{realm}/users", realm)
                    .headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(rep).retrieve().toBodilessEntity();
            return lastSegment(res.getHeaders().getLocation());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                return findUserId(realm, username)
                        .orElseThrow(() -> new IllegalStateException("A felhasználó létezik, de nem található: " + username));
            }
            throw e;
        }
    }

    public void updateUser(String realm, String userId, String email, String fullName, boolean enabled) {
        String[] names = splitName(fullName);
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("email", email);
        rep.put("firstName", names[0]);
        rep.put("lastName", names[1]);
        rep.put("enabled", enabled);
        http.put().uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(rep).retrieve().toBodilessEntity();
    }

    public void setUserEnabled(String realm, String userId, boolean enabled) {
        http.put().uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled", enabled)).retrieve().toBodilessEntity();
    }

    public void resetPassword(String realm, String userId, String password, boolean temporary) {
        http.put().uri("/admin/realms/{realm}/users/{id}/reset-password", realm, userId)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", password, "temporary", temporary))
                .retrieve().toBodilessEntity();
    }

    /** Bizalmas kliens szolgáltatásfiókkal (OAuth2 client credentials) egy integrációhoz. */
    public ServiceClient createServiceClient(String realm, String clientId, String description) {
        Map<String, Object> rep = new LinkedHashMap<>();
        rep.put("clientId", clientId);
        rep.put("name", description);
        rep.put("enabled", true);
        rep.put("publicClient", false);
        rep.put("serviceAccountsEnabled", true);
        rep.put("standardFlowEnabled", false);
        rep.put("directAccessGrantsEnabled", false);
        rep.put("clientAuthenticatorType", "client-secret");
        ResponseEntity<Void> res = http.post().uri("/admin/realms/{realm}/clients", realm)
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(rep).retrieve().toBodilessEntity();
        String id = lastSegment(res.getHeaders().getLocation());
        String secret = clientSecret(realm, id);
        JsonNode sa = http.get().uri("/admin/realms/{realm}/clients/{id}/service-account-user", realm, id)
                .headers(h -> h.setBearerAuth(token())).retrieve().body(JsonNode.class);
        return new ServiceClient(id, clientId, secret, sa.path("id").asText());
    }

    public String clientSecret(String realm, String clientUuid) {
        JsonNode s = http.get().uri("/admin/realms/{realm}/clients/{id}/client-secret", realm, clientUuid)
                .headers(h -> h.setBearerAuth(token())).retrieve().body(JsonNode.class);
        return s.path("value").asText();
    }

    public String regenerateClientSecret(String realm, String clientUuid) {
        JsonNode s = http.post().uri("/admin/realms/{realm}/clients/{id}/client-secret", realm, clientUuid)
                .headers(h -> h.setBearerAuth(token())).retrieve().body(JsonNode.class);
        return s.path("value").asText();
    }

    public void deleteClient(String realm, String clientUuid) {
        http.delete().uri("/admin/realms/{realm}/clients/{id}", realm, clientUuid)
                .headers(h -> h.setBearerAuth(token())).retrieve().toBodilessEntity();
    }

    private static String lastSegment(URI location) {
        if (location == null) {
            throw new IllegalStateException("A Keycloak nem adott vissza Location fejlécet");
        }
        String p = location.getPath();
        return p.substring(p.lastIndexOf('/') + 1);
    }

    private static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"-", "-"};
        }
        String n = fullName.trim();
        int i = n.indexOf(' ');
        return i < 0 ? new String[]{n, "-"} : new String[]{n.substring(0, i), n.substring(i + 1).trim()};
    }
}
