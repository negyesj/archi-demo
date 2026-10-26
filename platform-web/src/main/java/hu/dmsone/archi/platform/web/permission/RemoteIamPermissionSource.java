package hu.dmsone.archi.platform.web.permission;

import hu.dmsone.archi.platform.security.EffectivePermissions;
import hu.dmsone.archi.platform.web.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Az IAM {@code /me/context} végpontját hívja a felhasználó saját tokenjével. */
public class RemoteIamPermissionSource implements PermissionSource {

    private static final Logger log = LoggerFactory.getLogger(RemoteIamPermissionSource.class);
    private final RestClient client;

    public RemoteIamPermissionSource(RestClient.Builder builder, String iamBaseUrl) {
        this.client = builder.clone().baseUrl(iamBaseUrl).build();
    }

    @Override
    public EffectivePermissions load(String tenant, String subject) {
        try {
            EffectivePermissions p = client.get()
                    .uri("/api/t/{tenant}/iam/me/context", tenant)
                    .header("Authorization", "Bearer " + CurrentUser.bearerToken())
                    .retrieve()
                    .body(EffectivePermissions.class);
            return p == null ? EffectivePermissions.none(subject) : p;
        } catch (RestClientResponseException e) {
            log.warn("Jogosultság lekérés sikertelen ({}): tenant={}, subject={}", e.getStatusCode(), tenant, subject);
            return EffectivePermissions.none(subject);
        }
    }
}
