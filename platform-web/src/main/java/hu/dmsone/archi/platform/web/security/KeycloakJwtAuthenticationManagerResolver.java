package hu.dmsone.archi.platform.web.security;

import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Több Keycloak realm (tenantonként egy) tokenjeit fogadja el. A kibocsátót ({@code iss}) a publikus
 * Keycloak címhez méri, a kulcsokat viszont a belső címről tölti le (konténer hálózatban más a kettő).
 */
public class KeycloakJwtAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private final BearerTokenResolver tokenResolver = new DefaultBearerTokenResolver();
    private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();
    private final String issuerPrefix;
    private final String jwksBase;

    public KeycloakJwtAuthenticationManagerResolver(String issuerBase, String jwksBase) {
        this.issuerPrefix = trimSlash(issuerBase) + "/realms/";
        this.jwksBase = trimSlash(jwksBase);
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        String issuer = issuerOf(tokenResolver.resolve(request));
        if (issuer == null || !issuer.startsWith(issuerPrefix)) {
            return authentication -> {
                throw new InvalidBearerTokenException("Ismeretlen token kibocsátó");
            };
        }
        return managers.computeIfAbsent(issuer, this::build);
    }

    private AuthenticationManager build(String issuer) {
        String realm = issuer.substring(issuerPrefix.length());
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwksBase + "/realms/" + realm + "/protocol/openid-connect/certs")
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return new ProviderManager(new JwtAuthenticationProvider(decoder));
    }

    private static String issuerOf(String token) {
        if (token == null) {
            return null;
        }
        try {
            return JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
