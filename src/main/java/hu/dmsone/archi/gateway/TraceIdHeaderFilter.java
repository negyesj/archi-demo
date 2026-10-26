package hu.dmsone.archi.gateway;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** A műveleti azonosítót ({@code traceId}) visszaadja a válasz fejlécében, hogy a felület megjeleníthesse. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TraceIdHeaderFilter implements WebFilter {

    private final ObjectProvider<Tracer> tracer;

    public TraceIdHeaderFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Tracer t = tracer.getIfAvailable();
        if (t != null) {
            Span span = t.currentSpan();
            if (span != null) {
                exchange.getResponse().getHeaders().set("X-Trace-Id", span.context().traceId());
            }
        }
        return chain.filter(exchange);
    }
}
