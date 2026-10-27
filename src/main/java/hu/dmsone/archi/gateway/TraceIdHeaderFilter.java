package hu.dmsone.archi.gateway;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * A műveleti azonosítót ({@code traceId}) visszaadja a válasz fejlécében, hogy a felület megjeleníthesse.
 * A válasz elküldése előtt állítja be (ekkor már biztosan létezik a kérés megfigyelése), ha a szolgáltatás
 * válasza még nem tartalmazza.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TraceIdHeaderFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            if (!exchange.getResponse().getHeaders().containsKey("X-Trace-Id")) {
                String traceId = TenantGatewayFilter.traceIdOf(exchange);
                if (traceId != null) {
                    exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
                }
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
