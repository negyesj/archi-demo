package hu.dmsone.archi.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import hu.dmsone.archi.platform.trail.TrailKind;
import hu.dmsone.archi.platform.trail.TrailRecorder;
import hu.dmsone.archi.platform.trail.TrailStep;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
    private final TrailRecorder trail;

    public TenantGatewayFilter(TenantRegistry registry, GatewayProperties props, ObjectMapper objectMapper,
                               ObjectProvider<TrailRecorder> trail) {
        this.registry = registry;
        this.props = props;
        this.objectMapper = objectMapper;
        this.trail = trail.getIfAvailable();
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
        return traced(exchange, tenant, checkTenant(exchange, chain, tenant, service));
    }

    private Mono<Void> checkTenant(ServerWebExchange exchange, GatewayFilterChain chain, String tenant, String service) {
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

    /**
     * A művelet gyökérlépése a trailben: a gateway teljes válaszideje, a felhasználó és a státusz.
     * A {@code traceId} ugyanaz, mint a szolgáltatásokban és az aszinkron (Kafka) lépésekben.
     */
    private Mono<Void> traced(ServerWebExchange exchange, String tenant, Mono<Void> inner) {
        if (trail == null || !trail.enabled()) {
            return inner;
        }
        Instant started = Instant.now();
        long t0 = System.nanoTime();
        AtomicReference<String> user = new AtomicReference<>();
        String operation = exchange.getRequest().getMethod().name() + " " + normalize(exchange.getRequest().getPath().value());
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .doOnNext(t -> user.set(userOf(t)))
                .then(inner)
                .doFinally(signal -> {
                    var code = exchange.getResponse().getStatusCode();
                    int status = code == null ? 200 : code.value();
                    String traceId = traceIdOf(exchange);
                    trail.record(new TrailStep(tenant, traceId != null ? traceId : TrailRecorder.newId(16),
                            TrailRecorder.newId(8), null, trail.service(),
                            TrailKind.GATEWAY, operation, props.mode(), started, (System.nanoTime() - t0) / 1_000_000,
                            null, null, status >= 500 ? "ERROR" : "OK", "HTTP " + status, true, user.get()));
                });
    }

    /**
     * A kérés trace azonosítója: a szerver oldali megfigyelés (observation) spanjából, amelynek a gyermekeként
     * a gateway a {@code traceparent} fejlécet a szolgáltatásoknak továbbítja. Reaktív láncban a
     * {@code Tracer.currentSpan()} nem megbízható (nincs ThreadLocal), ezért nem azt használjuk.
     * Végső tartalék: a szolgáltatás válaszának {@code X-Trace-Id} fejléce.
     */
    static String traceIdOf(ServerWebExchange exchange) {
        String fromObservation = ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                .map(c -> (TracingObservationHandler.TracingContext) c.get(TracingObservationHandler.TracingContext.class))
                .map(tc -> tc.getSpan())
                .map(s -> s.context().traceId())
                .filter(id -> !id.isBlank())
                .orElse(null);
        return fromObservation != null ? fromObservation : exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
    }

    private static String userOf(JwtAuthenticationToken t) {
        String u = t.getToken().getClaimAsString("preferred_username");
        return u != null ? u : t.getToken().getClaimAsString("azp");
    }

    /** {@code /api/t/acme/irat/kuldemenyek/<uuid>/iktatas} → {@code /api/t/{tenant}/irat/kuldemenyek/{id}/iktatas}. */
    static String normalize(String path) {
        return path.replaceFirst("^/(api|v1)/t/[a-z][a-z0-9]{1,29}/", "/$1/t/{tenant}/")
                .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)", "/{id}")
                .replaceAll("/[0-9]+(?=/|$)", "/{n}");
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
        body.put("traceId", traceIdOf(exchange));
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
