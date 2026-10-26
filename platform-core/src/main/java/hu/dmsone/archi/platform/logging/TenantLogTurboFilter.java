package hu.dmsone.archi.platform.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import hu.dmsone.archi.platform.tenant.TenantContext;
import org.slf4j.MDC;
import org.slf4j.Marker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenantra szűkített részletes naplózás: ha az aktuális kérés tenantjára DEBUG (vagy más) szint van beállítva,
 * az adott logger-előtag alatti üzeneteket a logger saját szintjétől függetlenül kiírja.
 */
public class TenantLogTurboFilter extends TurboFilter {

    private record Rule(String loggerPrefix, Level level) { }

    private final Map<String, Rule> rules = new ConcurrentHashMap<>();

    public void set(String tenant, String loggerPrefix, String level) {
        if (level == null) {
            rules.remove(tenant);
        } else {
            rules.put(tenant, new Rule(loggerPrefix == null || "ROOT".equals(loggerPrefix) ? "" : loggerPrefix,
                    Level.toLevel(level, Level.DEBUG)));
        }
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
        if (rules.isEmpty()) {
            return FilterReply.NEUTRAL;
        }
        String tenant = MDC.get(TenantContext.MDC_KEY);
        if (tenant == null) {
            return FilterReply.NEUTRAL;
        }
        Rule rule = rules.get(tenant);
        if (rule != null && logger.getName().startsWith(rule.loggerPrefix()) && level.isGreaterOrEqual(rule.level())) {
            return FilterReply.ACCEPT;
        }
        return FilterReply.NEUTRAL;
    }
}
