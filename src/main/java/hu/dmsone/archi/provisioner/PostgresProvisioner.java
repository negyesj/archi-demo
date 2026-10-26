package hu.dmsone.archi.provisioner;

import hu.dmsone.archi.platform.db.SecretProvider;
import hu.dmsone.archi.platform.tenant.TenantNames;
import hu.dmsone.archi.platform.util.Passwords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Tenant PostgreSQL adatbázis: saját szerep (csak a saját adatbázisához fér hozzá) és saját adatbázis,
 * amelynek a szerep a tulajdonosa (így a szolgáltatások létrehozhatják a sémáikat).
 * Idempotens: újrafuttatáskor a meglévő erőforrásokat használja, a jelszót újraszinkronizálja.
 */
@Component
public class PostgresProvisioner {

    private static final Logger log = LoggerFactory.getLogger(PostgresProvisioner.class);

    private final ProvisionerProperties props;
    private final SecretProvider secrets;

    public PostgresProvisioner(ProvisionerProperties props, SecretProvider secrets) {
        this.props = props;
        this.secrets = secrets;
    }

    public void provision(String code) throws SQLException {
        String db = TenantNames.pgDatabase(code);
        String role = TenantNames.pgUser(code);
        String password = secrets.get(TenantNames.pgSecretKey(code)).orElseGet(() -> Passwords.generate(24));

        try (Connection c = DriverManager.getConnection(props.postgresAdminUrl(), props.postgresAdminUser(),
                props.postgresAdminPassword())) {
            c.setAutoCommit(true);
            try (Statement st = c.createStatement()) {
                // A nevek a TenantNames szabályai szerint csak [a-z0-9_] karakterek, a jelszó base64url: SQL-biztos.
                if (exists(c, "select 1 from pg_roles where rolname = ?", role)) {
                    st.execute("alter role " + role + " with login password '" + password + "'");
                } else {
                    st.execute("create role " + role + " with login password '" + password + "'");
                }
                if (!exists(c, "select 1 from pg_database where datname = ?", db)) {
                    st.execute("create database " + db + " owner " + role);
                }
                st.execute("revoke all on database " + db + " from public");
                st.execute("grant connect, create, temporary on database " + db + " to " + role);
            }
        }
        String tenantDbUrl = props.postgresAdminUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + db + "$1");
        try (Connection c = DriverManager.getConnection(tenantDbUrl, props.postgresAdminUser(), props.postgresAdminPassword());
             Statement st = c.createStatement()) {
            st.execute("create extension if not exists pg_trgm");
            st.execute("revoke create on schema public from public");
        }
        secrets.put(TenantNames.pgSecretKey(code), password);
        log.info("PostgreSQL kész: {}", db);
    }

    private static boolean exists(Connection c, String sql, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
