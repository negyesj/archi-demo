package hu.dmsone.archi.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minden tenant-útvonalon ellenőrzi: létezik és aktív a tenant, a szolgáltatás engedélyezett nála,
 * és a token ugyanahhoz a tenanthoz (realmhez) tartozik. Az admin API-t csak a platform realm érheti el.
 */
@Component
public class TenantGatewayFilter implements GlobalFilter, Ordered {

    private static final Pattern TENANT_PATH = Pattern.compile("^/(?:api|v1)/t/([a-z][a-z0-9]{1,29})/([a-z0-9-]+)(?:/.*)?$");
    public static final String PLATFORM_REALM = "platform";

    private final TenantRegistry registry;
    private final GatewayProperties props;
    private final ObjectMapper objectMapper;

    public TenantGatewayFilter(TenantRegistry registry, GatewayProperties props, ObjectMapper objectMapper) {
        this.registry = registry;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/admin/")) {
            return realmOrEmpty(exchange).flatMap(realm -> {
                if (realm.isEmpty()) {
                    return error(exchange, HttpStatus.UNAUTHORIZED, "Bejelentkezés szükséges", "Hiányzó token.");
                }
                return PLATFORM_REALM.equals(realm.get())
                        ? chain.filter(exchange)
                        : error(exchange, HttpStatus.FORBIDDEN, "Nincs jogosultság", "Az admin API csak platform felhasználóknak érhető el.");
            });
        }
        Matcher m = TENANT_PATH.matcher(path);
        if (!m.matches()) {
            return chain.filter(exchange);
        }
        String tenant = m.group(1);
        String service = m.group(2);
        Optional<TenantInfo> info = registry.findActive(tenant);
        if (info.isEmpty()) {
            return error(exchange, HttpStatus.NOT_FOUND, "Ismeretlen tenant", "A tenant nem létezik vagy nem aktív.");
        }
        if (!props.coreServices().contains(service) && !info.get().serviceEnabled(service)) {
            return error(exchange, HttpStatus.FORBIDDEN, "Szolgáltatás nincs engedélyezve",
                    "A(z) " + service + " szolgáltatás nincs engedélyezve ennél a tenantnál.");
        }
        return realmOrEmpty(exchange).flatMap(realm -> realm.isEmpty() || realm.get().equals(tenant)
                ? chain.filter(exchange)
                : error(exchange, HttpStatus.FORBIDDEN, "Tenant eltérés", "A token másik tenanthoz tartozik."));
    }

    /** A token realmje; token nélkül üres Optional (a Mono mindig pontosan egy elemet ad). */
    private Mono<Optional<String>> realmOrEmpty(ServerWebExchange exchange) {
        return realm(exchange).map(Optional::of).defaultIfEmpty(Optional.empty());
    }

    private Mono<String> realm(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(t -> {
                    String iss = t.getToken().getIssuer() == null ? "" : t.getToken().getIssuer().toString();
                    return iss.substring(iss.lastIndexOf('/') + 1);
                });
    }

    private Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String title, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("traceId", exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = detail.getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
