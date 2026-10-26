package hu.dmsone.archi.platform.web.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/** Az aktuális kérés hitelesített felhasználója (Keycloak JWT). */
public final class CurrentUser {

    public static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }

    public static boolean isAuthenticated() {
        return jwt().isPresent();
    }

    /** A token realmje (= tenant kód, vagy {@code platform} az admin felületnél). */
    public static Optional<String> realm() {
        return jwt().map(j -> j.getIssuer() == null ? null : j.getIssuer().toString())
                .map(iss -> iss.substring(iss.lastIndexOf('/') + 1));
    }

    public static String subject() {
        return jwt().map(Jwt::getSubject).orElse(null);
    }

    public static String username() {
        return jwt().map(j -> j.getClaimAsString("preferred_username")).orElse(null);
    }

    public static String displayName() {
        return jwt().map(j -> {
            String name = j.getClaimAsString("name");
            return name != null ? name : j.getClaimAsString("preferred_username");
        }).orElse(null);
    }

    /** OAuth2 client credentials token (API kliens) esetén igaz. */
    public static boolean isServiceAccount() {
        String u = username();
        return u != null && u.startsWith("service-account-");
    }

    public static String clientId() {
        return jwt().map(j -> j.getClaimAsString("azp")).orElse(null);
    }

    public static String bearerToken() {
        return jwt().map(Jwt::getTokenValue).orElse(null);
    }

    private CurrentUser() { }
}
