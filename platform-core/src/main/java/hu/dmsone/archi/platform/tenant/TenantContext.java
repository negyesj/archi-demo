package hu.dmsone.archi.platform.tenant;

import org.slf4j.MDC;

import java.util.function.Supplier;

/**
 * Az aktuális szál tenant-kontextusa. HTTP kérésnél a tenant szűrő, Kafka fogyasztásnál a fogyasztó tölti ki
 * az esemény {@code tenantId} mezőjéből.
 */
public final class TenantContext {

    public static final String MDC_KEY = "tenantId";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String tenant) {
        CURRENT.set(tenant);
        if (tenant == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, tenant);
        }
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String require() {
        String t = CURRENT.get();
        if (t == null) {
            throw new IllegalStateException("Nincs tenant kontextus az aktuális szálon");
        }
        return t;
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(MDC_KEY);
    }

    public static <T> T callAs(String tenant, Supplier<T> action) {
        String previous = CURRENT.get();
        set(tenant);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }

    public static void runAs(String tenant, Runnable action) {
        callAs(tenant, () -> {
            action.run();
            return null;
        });
    }

    private TenantContext() { }
}
