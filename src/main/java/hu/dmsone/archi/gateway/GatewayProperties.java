package hu.dmsone.archi.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @param mode           {@code ui}: a felület belső API-ja ({@code /api/t/{tenant}/{szolgaltatas}/**}),
 *                       {@code public}: a publikus integrációs API ({@code /v1/t/{tenant}/{szolgaltatas}/**})
 * @param services       szolgáltatás azonosító → belső URI
 * @param coreServices   minden tenantnál elérhető alapszolgáltatások
 * @param adminUri       a tenant admin szolgáltatás (csak ui módban)
 * @param issuerBase     Keycloak publikus címe (token iss)
 * @param jwksBase       Keycloak belső címe
 * @param rateLimit      publikus API: kérés/másodperc és burst API kliensenként
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        String mode,
        Map<String, String> services,
        List<String> coreServices,
        String adminUri,
        String issuerBase,
        String jwksBase,
        RateLimit rateLimit) {

    public GatewayProperties {
        services = services == null ? new LinkedHashMap<>() : services;
        coreServices = coreServices == null ? List.of() : coreServices;
        rateLimit = rateLimit == null ? new RateLimit(20, 40) : rateLimit;
    }

    public boolean isPublic() {
        return "public".equalsIgnoreCase(mode);
    }

    public record RateLimit(int replenishRate, int burstCapacity) { }
}
