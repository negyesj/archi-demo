package hu.dmsone.archi.platform.db;

import hu.dmsone.archi.platform.PlatformProperties;
import hu.dmsone.archi.platform.tenant.TenantChangedEvent;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minden aktív tenant adatbázisában naprakészre hozza a szolgáltatás saját sémáit (Flyway).
 * Így minden szolgáltatás a saját migrációit hordozza, a kód nem keveredik.
 */
public class TenantMigrator {

    private static final Logger log = LoggerFactory.getLogger(TenantMigrator.class);
    public static final PlatformProperties.Migration OUTBOX =
            new PlatformProperties.Migration("outbox", "classpath:db/platform/outbox");

    private final TenantDataSources dataSources;
    private final List<PlatformProperties.Migration> migrations;
    private final ApplicationEventPublisher publisher;
    private final Set<String> migrated = ConcurrentHashMap.newKeySet();

    public TenantMigrator(TenantDataSources dataSources, List<PlatformProperties.Migration> serviceMigrations,
                          ApplicationEventPublisher publisher) {
        this.dataSources = dataSources;
        this.migrations = new ArrayList<>();
        this.migrations.add(OUTBOX);
        this.migrations.addAll(serviceMigrations);
        this.publisher = publisher;
    }

    @EventListener
    public void onTenantChanged(TenantChangedEvent event) {
        if (event.removed()) {
            migrated.remove(event.code());
            dataSources.evict(event.code());
            return;
        }
        if (event.tenant().isActive()) {
            migrate(event.tenant());
        }
    }

    public boolean isMigrated(String tenant) {
        return migrated.contains(tenant);
    }

    public synchronized void migrate(TenantInfo tenant) {
        if (migrated.contains(tenant.code())) {
            return;
        }
        try {
            DataSource ds = dataSources.get(tenant.code());
            for (PlatformProperties.Migration m : migrations) {
                Flyway.configure()
                        .dataSource(ds)
                        .schemas(m.getSchema())
                        .defaultSchema(m.getSchema())
                        .createSchemas(true)
                        .locations(m.getLocation())
                        .baselineOnMigrate(true)
                        .load()
                        .migrate();
            }
            migrated.add(tenant.code());
            log.info("Tenant adatbázis migrálva: {}", tenant.code());
            publisher.publishEvent(new TenantMigratedEvent(tenant));
        } catch (Exception e) {
            log.error("Tenant migráció sikertelen: {}", tenant.code(), e);
        }
    }
}
