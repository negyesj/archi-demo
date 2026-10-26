package hu.dmsone.archi.gateway;

import com.nimbusds.jwt.JWTParser;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tenantonkénti Keycloak realm tokenek elfogadása; a kulcsokat a belső Keycloak címről tölti le. */
public class ReactiveKeycloakResolver implements ReactiveAuthenticationManagerResolver<ServerWebExchange> {

    private final Map<String, ReactiveAuthenticationManager> managers = new ConcurrentHashMap<>();
    private final String issuerPrefix;
    private final String jwksBase;

    public ReactiveKeycloakResolver(String issuerBase, String jwksBase) {
        this.issuerPrefix = strip(issuerBase) + "/realms/";
        this.jwksBase = strip(jwksBase == null || jwksBase.isBlank() ? issuerBase : jwksBase);
    }

    @Override
    public Mono<ReactiveAuthenticationManager> resolve(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String issuer = null;
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            try {
                issuer = JWTParser.parse(header.substring(7).trim()).getJWTClaimsSet().getIssuer();
            } catch (Exception ignored) {
                issuer = null;
            }
        }
        if (issuer == null || !issuer.startsWith(issuerPrefix)) {
            return Mono.just(auth -> Mono.error(new InvalidBearerTokenException("Ismeretlen token kibocsátó")));
        }
        return Mono.just(managers.computeIfAbsent(issuer, this::build));
    }

    private ReactiveAuthenticationManager build(String issuer) {
        String realm = issuer.substring(issuerPrefix.length());
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(jwksBase + "/realms/" + realm + "/protocol/openid-connect/certs").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return new JwtReactiveAuthenticationManager(decoder);
    }

    private static String strip(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
