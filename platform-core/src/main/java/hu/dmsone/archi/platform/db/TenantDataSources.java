package hu.dmsone.archi.platform.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import hu.dmsone.archi.platform.PlatformProperties;
import hu.dmsone.archi.platform.tenant.TenantNames;
import hu.dmsone.archi.platform.trail.TrailJdbc;
import hu.dmsone.archi.platform.trail.TrailRecorder;
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
    private final Map<String, DataSource> measured = new ConcurrentHashMap<>();
    private final PlatformProperties.TenantDb props;
    private final SecretProvider secrets;
    private final TrailRecorder trail;

    public TenantDataSources(PlatformProperties.TenantDb props, SecretProvider secrets, TrailRecorder trail) {
        this.props = props;
        this.secrets = secrets;
        this.trail = trail;
    }

    /** A tenant adatbázisa; az SQL utasítások a műveleti trailbe is bekerülnek (ha van aktív trail kontextus). */
    public DataSource get(String tenant) {
        return measured.computeIfAbsent(tenant, t -> TrailJdbc.wrap(raw(t), TenantNames.pgDatabase(t), trail));
    }

    /** A tenant pool mérés nélkül (migrációhoz). */
    public DataSource raw(String tenant) {
        return pools.computeIfAbsent(tenant, this::create);
    }

    public void evict(String tenant) {
        measured.remove(tenant);
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
        measured.clear();
    }
}
