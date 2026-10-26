package hu.dmsone.archi.platform.db;

import hu.dmsone.archi.platform.tenant.TenantContext;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/** A {@link TenantContext} alapján az aktuális tenant adatbázisára irányító DataSource. */
public class TenantRoutingDataSource extends AbstractDataSource {

    private final TenantDataSources dataSources;

    public TenantRoutingDataSource(TenantDataSources dataSources) {
        this.dataSources = dataSources;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSources.get(TenantContext.require()).getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Tenant adatbázishoz explicit felhasználóval nem lehet kapcsolódni");
    }
}
