package hu.dmsone.archi.irat;

import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.web.client.ServiceClients;
import hu.dmsone.archi.platform.web.error.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Szinkron ellenőrzés a függő szolgáltatásokban (a felhasználó tokenjével, a traceparent automatikusan továbbmegy).
 * Iktatásnál a zár megszerzése ELŐTT hívjuk, hogy a számláló zárolása alatt ne legyen távoli hívás.
 */
@Component
public class RemoteLookups {

    public record PartnerSummary(UUID id, String name, String taxNumber, String city, String address, boolean active) { }

    public record UserSummary(UUID id, String username, String fullName, String email, boolean active) { }

    private final RestClient partner;
    private final RestClient iam;

    public RemoteLookups(ServiceClients clients, IratProperties props) {
        this.partner = clients.create(props.partnerUrl());
        this.iam = clients.create(props.iamUrl());
    }

    public PartnerSummary activePartner(UUID id) {
        try {
            PartnerSummary p = partner.get()
                    .uri("/api/t/{tenant}/partner/partners/{id}/summary", TenantContext.require(), id)
                    .retrieve().body(PartnerSummary.class);
            if (p == null || !p.active()) {
                throw ApiException.unprocessable("A partner nem aktív");
            }
            return p;
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.unprocessable("A partner nem található: " + id);
        }
    }

    public UserSummary activeUser(UUID id) {
        try {
            UserSummary u = iam.get()
                    .uri("/api/t/{tenant}/iam/users/{id}/summary", TenantContext.require(), id)
                    .retrieve().body(UserSummary.class);
            if (u == null || !u.active()) {
                throw ApiException.unprocessable("Az ügyintéző nem aktív felhasználó");
            }
            return u;
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.unprocessable("Az ügyintéző nem található: " + id);
        }
    }
}
