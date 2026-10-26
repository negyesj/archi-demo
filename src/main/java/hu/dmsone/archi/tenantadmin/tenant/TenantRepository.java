package hu.dmsone.archi.tenantadmin.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.tenant.TenantStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class TenantRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public TenantRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<TenantRecord> findAll() {
        return jdbc.sql("select * from tenant order by code").query(this::map).list();
    }

    public Optional<TenantRecord> find(String code) {
        return jdbc.sql("select * from tenant where code = :code").param("code", code).query(this::map).optional();
    }

    public boolean exists(String code) {
        return jdbc.sql("select count(*) from tenant where code = :code").param("code", code)
                .query(Integer.class).single() > 0;
    }

    public void insert(TenantRecord t) {
        jdbc.sql("""
                insert into tenant (code, name, status, settings, admin_username, admin_email, admin_name, version)
                values (:code, :name, :status, cast(:settings as jsonb), :au, :ae, :an, 1)
                """)
                .param("code", t.code()).param("name", t.name()).param("status", t.status().name())
                .param("settings", json(t.settings()))
                .param("au", t.adminUsername()).param("ae", t.adminEmail()).param("an", t.adminName())
                .update();
        replaceServices(t.code(), t.enabledServices());
    }

    public void update(String code, String name, Map<String, String> settings) {
        jdbc.sql("""
                update tenant set name = :name, settings = cast(:settings as jsonb),
                       version = version + 1, updated_at = now() where code = :code
                """)
                .param("code", code).param("name", name).param("settings", json(settings)).update();
    }

    public void updateStatus(String code, TenantStatus status, String lastError) {
        jdbc.sql("""
                update tenant set status = :status, last_error = :err, version = version + 1, updated_at = now()
                where code = :code
                """)
                .param("code", code).param("status", status.name()).param("err", lastError).update();
    }

    public void markProvisioned(String code, String adminKeycloakId) {
        jdbc.sql("""
                update tenant set status = 'ACTIVE', admin_keycloak_id = :kid, last_error = null,
                       version = version + 1, updated_at = now() where code = :code
                """)
                .param("code", code).param("kid", adminKeycloakId).update();
    }

    public void replaceServices(String code, Set<String> services) {
        jdbc.sql("delete from tenant_service where tenant_code = :code").param("code", code).update();
        for (String s : services) {
            jdbc.sql("insert into tenant_service (tenant_code, service_id) values (:code, :s)")
                    .param("code", code).param("s", s).update();
        }
        jdbc.sql("update tenant set version = version + 1, updated_at = now() where code = :code")
                .param("code", code).update();
    }

    private Set<String> services(String code) {
        return new HashSet<>(jdbc.sql("select service_id from tenant_service where tenant_code = :code")
                .param("code", code).query(String.class).list());
    }

    private TenantRecord map(ResultSet rs, int i) throws SQLException {
        String code = rs.getString("code");
        Map<String, String> settings;
        try {
            settings = objectMapper.readValue(rs.getString("settings"), new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            settings = Map.of();
        }
        return new TenantRecord(code, rs.getString("name"), TenantStatus.valueOf(rs.getString("status")), settings,
                services(code), rs.getString("admin_username"), rs.getString("admin_email"), rs.getString("admin_name"),
                rs.getString("admin_keycloak_id"), rs.getString("last_error"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
