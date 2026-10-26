package hu.dmsone.archi.tenantadmin.adminuser;

import hu.dmsone.archi.tenantadmin.security.AdminUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AdminUserRepository {

    private final JdbcClient jdbc;

    public AdminUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AdminUser> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return jdbc.sql("select * from admin_user where username = :u").param("u", username).query(this::map).optional();
    }

    public Optional<AdminUser> find(UUID id) {
        return jdbc.sql("select * from admin_user where id = :id").param("id", id).query(this::map).optional();
    }

    public List<AdminUser> findAll() {
        return jdbc.sql("select * from admin_user order by username").query(this::map).list();
    }

    public void insert(AdminUser u) {
        jdbc.sql("""
                insert into admin_user (id, username, email, full_name, role, active, keycloak_id)
                values (:id, :username, :email, :name, :role, :active, :kid)
                """)
                .param("id", u.id()).param("username", u.username()).param("email", u.email())
                .param("name", u.fullName()).param("role", u.role()).param("active", u.active())
                .param("kid", u.keycloakId())
                .update();
    }

    public void update(UUID id, String email, String fullName, String role, boolean active) {
        jdbc.sql("""
                update admin_user set email = :email, full_name = :name, role = :role, active = :active,
                       updated_at = now() where id = :id
                """)
                .param("id", id).param("email", email).param("name", fullName).param("role", role)
                .param("active", active).update();
    }

    private AdminUser map(ResultSet rs, int i) throws SQLException {
        return new AdminUser(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("email"),
                rs.getString("full_name"), rs.getString("role"), rs.getBoolean("active"), rs.getString("keycloak_id"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
