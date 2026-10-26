package hu.dmsone.archi.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Map;

/** Útvonalak: szolgáltatásonként egy route; a publikus módban átírás és API kliensenkénti terheléskorlát. */
@Configuration
public class RouteConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter(GatewayProperties props) {
        return new RedisRateLimiter(props.rateLimit().replenishRate(), props.rateLimit().burstCapacity());
    }

    /** Terheléskorlát kulcsa: az API kliens (token principal), ennek hiányában a kliens IP címe. */
    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> exchange.getRequest().getRemoteAddress() == null
                        ? "anonymous" : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()));
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties props,
                               RedisRateLimiter rateLimiter, KeyResolver keyResolver) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        for (Map.Entry<String, String> e : props.services().entrySet()) {
            String id = e.getKey();
            String uri = e.getValue();
            if (props.isPublic()) {
                routes = routes.route("public-" + id, r -> r
                        .path("/v1/t/*/" + id + "/**")
                        .filters(f -> f
                                .rewritePath("/v1/t/(?<tenant>[^/]+)/(?<rest>.*)", "/public/v1/t/${tenant}/${rest}")
                                .requestRateLimiter(c -> c.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                        .uri(uri));
            } else {
                routes = routes.route("ui-" + id, r -> r.path("/api/t/*/" + id + "/**").uri(uri));
            }
        }
        if (!props.isPublic() && props.adminUri() != null) {
            routes = routes.route("admin", r -> r.path("/api/admin/**").uri(props.adminUri()));
        }
        return routes.build();
    }
}
