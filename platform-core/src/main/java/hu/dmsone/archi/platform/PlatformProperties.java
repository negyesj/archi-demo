package hu.dmsone.archi.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A platform közös beállításai ({@code platform.*}).
 * Minden környezetfüggő érték környezeti változóból jön (12-factor), lásd a szolgáltatások application.yml-jét.
 */
@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    /** A szolgáltatás azonosítója a katalógusban és az eseményekben (pl. {@code partner}). */
    private String serviceId;

    private final TenantDb tenantDb = new TenantDb();
    private final PlatformDb platformDb = new PlatformDb();
    private final Secrets secrets = new Secrets();
    private final Outbox outbox = new Outbox();

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public TenantDb getTenantDb() { return tenantDb; }
    public PlatformDb getPlatformDb() { return platformDb; }
    public Secrets getSecrets() { return secrets; }
    public Outbox getOutbox() { return outbox; }

    /** Tenantonkénti PostgreSQL adatbázisok elérése. */
    public static class TenantDb {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 5432;
        private int maxPoolSize = 5;
        /** A szolgáltatás saját sémái és Flyway migrációs útvonalai. */
        private List<Migration> migrations = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public List<Migration> getMigrations() { return migrations; }
        public void setMigrations(List<Migration> migrations) { this.migrations = migrations; }
    }

    public static class Migration {
        private String schema;
        private String location;

        public Migration() { }
        public Migration(String schema, String location) { this.schema = schema; this.location = location; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
    }

    /** A platform adatbázis (titoktár olvasásához). */
    public static class PlatformDb {
        private String url;
        private String username;
        private String password;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Secrets {
        /** Base64 kódolt 256 bites AES kulcs a titoktár titkosításához. */
        private String key;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
    }

    public static class Outbox {
        /** Ha üres: {@code domain.<serviceId>.events}. */
        private String topic;

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }
}
