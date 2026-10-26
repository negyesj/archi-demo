package hu.dmsone.archi.platform.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import hu.dmsone.archi.platform.PlatformProperties;
import hu.dmsone.archi.platform.tenant.TenantNames;
import org.springframework.beans.factory.DisposableBean;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenantonként külön, lustán létrehozott, kis méretű connection pool a tenant saját adatbázisához,
 * a tenant saját DB-felhasználójával.
 */
public class TenantDataSources implements DisposableBean {

    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final PlatformProperties.TenantDb props;
    private final SecretProvider secrets;

    public TenantDataSources(PlatformProperties.TenantDb props, SecretProvider secrets) {
        this.props = props;
        this.secrets = secrets;
    }

    public DataSource get(String tenant) {
        return pools.computeIfAbsent(tenant, this::create);
    }

    public void evict(String tenant) {
        HikariDataSource ds = pools.remove(tenant);
        if (ds != null) {
            ds.close();
        }
    }

    private HikariDataSource create(String tenant) {
        String password = secrets.get(TenantNames.pgSecretKey(tenant))
                .orElseThrow(() -> new IllegalStateException("Nincs adatbázis titok a tenanthoz: " + tenant));
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl("jdbc:postgresql://" + props.getHost() + ":" + props.getPort() + "/" + TenantNames.pgDatabase(tenant));
        c.setUsername(TenantNames.pgUser(tenant));
        c.setPassword(password);
        c.setMaximumPoolSize(props.getMaxPoolSize());
        c.setMinimumIdle(0);
        c.setIdleTimeout(120_000);
        c.setPoolName("tenant-" + tenant);
        return new HikariDataSource(c);
    }

    @Override
    public void destroy() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
