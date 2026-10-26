package hu.dmsone.archi.gateway;

import hu.dmsone.archi.platform.tenant.TenantRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bejelentkezés előtti, nyilvános tenant információ a tenant választóhoz és a login oldalhoz.
 * Nem listázza a tenantokat: csak egy ismert kódra válaszol, nem létező / inaktív kódra 404.
 */
@RestController
public class TenantInfoController {

    private final TenantRegistry registry;
    private final GatewayProperties props;

    public TenantInfoController(TenantRegistry registry, GatewayProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @GetMapping("/api/tenants/{code}")
    public Mono<ResponseEntity<Map<String, Object>>> info(@PathVariable String code) {
        return Mono.just(registry.findActive(code)
                .map(t -> {
                    Set<String> services = new TreeSet<>(t.enabledServices());
                    services.addAll(props.coreServices());
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "code", t.code(),
                            "name", t.name(),
                            "services", services));
                })
                .orElse(ResponseEntity.notFound().build()));
    }
}
