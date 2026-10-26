package hu.dmsone.archi.platform.kafka;

/** A platform Kafka topicjai. Lásd: architektúra terv, Aszinkron eseménykezelés fejezet. */
public final class Topics {
    /** Tenantok aktuális állapota, compacted, kulcs: tenant kód. */
    public static final String TENANTS = "platform.tenants";
    /** Tenant admin → provisioner parancsok. */
    public static final String TENANT_COMMANDS = "platform.tenant.commands";
    /** Provisioner → tenant admin visszajelzések. */
    public static final String TENANT_EVENTS = "platform.tenant.events";
    /** Szolgáltatás manifestek, compacted, kulcs: szolgáltatás azonosító. */
    public static final String CATALOG = "platform.catalog";
    /** Loglevel beállítások, compacted. */
    public static final String LOGLEVEL = "platform.loglevel";
    /** Tenant admin műveletek auditja. */
    public static final String PLATFORM_AUDIT = "platform.audit";
    /** IAM CRUD események. */
    public static final String IAM_EVENTS = "iam.events";
    /** Az üzleti szolgáltatások eseménytopicjainak mintája. */
    public static final String DOMAIN_PATTERN = "domain\\..*\\.events|iam\\.events";

    public static String domain(String serviceId) {
        return "domain." + serviceId + ".events";
    }

    private Topics() { }
}
