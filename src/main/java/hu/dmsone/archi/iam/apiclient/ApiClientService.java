package hu.dmsone.archi.iam.apiclient;

import hu.dmsone.archi.iam.common.IamChanges;
import hu.dmsone.archi.iam.user.UserDtos;
import hu.dmsone.archi.iam.user.UserService;
import hu.dmsone.archi.platform.keycloak.KeycloakAdminClient;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantNames;
import hu.dmsone.archi.platform.web.error.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Integrációs API kliensek: Keycloak bizalmas kliens (OAuth2 client credentials) + technikai felhasználó az IAM-ben.
 * A technikai felhasználóhoz ugyanúgy rendelhetők jogosultsági elemek, mint egy emberhez.
 */
@Service
public class ApiClientService {

    public record ApiClient(UUID id, String clientId, String name, UUID userId, boolean active, Instant createdAt) { }

    public record CreateRequest(@NotBlank @Size(max = 200) String name,
                                @NotBlank @Size(max = 60) @Pattern(regexp = "^[a-z0-9][a-z0-9-]+$") String clientId) { }

    public record Credentials(ApiClient client, String clientSecret, String tokenUrl) { }

    private final JdbcClient jdbc;
    private final KeycloakAdminClient keycloak;
    private final UserService users;
    private final IamChanges changes;

    public ApiClientService(JdbcClient jdbc, KeycloakAdminClient keycloak, UserService users, IamChanges changes) {
        this.jdbc = jdbc;
        this.keycloak = keycloak;
        this.users = users;
        this.changes = changes;
    }

    public List<ApiClient> list() {
        return jdbc.sql("select * from iam.api_client order by name").query((rs, i) -> new ApiClient(
                rs.getObject("id", UUID.class), rs.getString("client_id"), rs.getString("name"),
                rs.getObject("user_id", UUID.class), rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    private ApiClient get(UUID id) {
        return list().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("API kliens"));
    }

    private String keycloakUuid(UUID id) {
        return jdbc.sql("select keycloak_client_uuid from iam.api_client where id = :id").param("id", id)
                .query(String.class).single();
    }

    @Transactional
    public Credentials create(CreateRequest req, String publicIssuerBase) {
        String realm = TenantNames.realm(TenantContext.require());
        String clientId = "api-" + req.clientId();
        KeycloakAdminClient.ServiceClient sc = keycloak.createServiceClient(realm, clientId, req.name());
        UserDtos.User tech = users.createTechnical(sc.serviceAccountUserId(), "service-account-" + clientId, req.name());
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into iam.api_client (id, client_id, name, keycloak_client_uuid, user_id)
                values (:id, :cid, :name, :kc, :uid)
                """).param("id", id).param("cid", clientId).param("name", req.name()).param("kc", sc.id())
                .param("uid", tech.id()).update();
        ApiClient c = get(id);
        changes.record("ApiClient", id.toString(), "CREATE", System.currentTimeMillis(), null, c, false);
        return new Credentials(c, sc.secret(), tokenUrl(publicIssuerBase, realm));
    }

    @Transactional
    public Credentials rotateSecret(UUID id, String publicIssuerBase) {
        ApiClient c = get(id);
        String realm = TenantNames.realm(TenantContext.require());
        String secret = keycloak.regenerateClientSecret(realm, keycloakUuid(id));
        changes.record("ApiClient", id.toString(), "ROTATE_SECRET", System.currentTimeMillis(), null, c, false);
        return new Credentials(c, secret, tokenUrl(publicIssuerBase, realm));
    }

    /** Visszavonás: a Keycloak kliens törlődik (a token nem kérhető többé), a technikai felhasználó inaktív lesz. */
    @Transactional
    public void revoke(UUID id) {
        ApiClient c = get(id);
        keycloak.deleteClient(TenantNames.realm(TenantContext.require()), keycloakUuid(id));
        jdbc.sql("update iam.api_client set active = false where id = :id").param("id", id).update();
        users.delete(c.userId());
        changes.record("ApiClient", id.toString(), "REVOKE", System.currentTimeMillis(), c, null, true);
    }

    private static String tokenUrl(String issuerBase, String realm) {
        return issuerBase + "/realms/" + realm + "/protocol/openid-connect/token";
    }
}
