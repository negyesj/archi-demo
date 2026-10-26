package hu.dmsone.archi.iam.user;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserDtos.User> find(UUID id) {
        return jdbc.sql("select * from iam.app_user where id = :id").param("id", id).query(this::map).optional();
    }

    public Optional<UserDtos.User> findByKeycloakId(String keycloakId) {
        return jdbc.sql("select * from iam.app_user where keycloak_id = :k").param("k", keycloakId).query(this::map).optional();
    }

    public Optional<UserDtos.User> findByUsername(String username) {
        return jdbc.sql("select * from iam.app_user where username = :u").param("u", username).query(this::map).optional();
    }

    public List<UserDtos.User> search(String q, Boolean active, String type, int limit, int offset) {
        return jdbc.sql("select * from iam.app_user where " + where(q, active, type)
                        + " order by full_name limit :limit offset :offset")
                .param("q", like(q)).param("active", active).param("type", type)
                .param("limit", limit).param("offset", offset)
                .query(this::map).list();
    }

    public long count(String q, Boolean active, String type) {
        return jdbc.sql("select count(*) from iam.app_user where " + where(q, active, type))
                .param("q", like(q)).param("active", active).param("type", type)
                .query(Long.class).single();
    }

    private static String where(String q, Boolean active, String type) {
        StringBuilder w = new StringBuilder("1=1");
        if (q != null && !q.isBlank()) {
            w.append(" and (lower(full_name) like :q or lower(username) like :q or lower(coalesce(email,'')) like :q)");
        }
        if (active != null) {
            w.append(" and active = :active");
        }
        if (type != null && !type.isBlank()) {
            w.append(" and type = :type");
        }
        return w.toString();
    }

    private static String like(String q) {
        return q == null ? null : "%" + q.toLowerCase().trim() + "%";
    }

    public void insert(UserDtos.User u) {
        jdbc.sql("""
                insert into iam.app_user (id, keycloak_id, username, email, full_name, type, active, version)
                values (:id, :kid, :username, :email, :name, :type, :active, 0)
                """)
                .param("id", u.id()).param("kid", u.keycloakId()).param("username", u.username())
                .param("email", u.email()).param("name", u.fullName()).param("type", u.type())
                .param("active", u.active())
                .update();
    }

    /** Optimista zárolással: ha közben más módosította, 0 sort érint. */
    public int update(UUID id, String email, String fullName, boolean active, long expectedVersion) {
        return jdbc.sql("""
                update iam.app_user set email = :email, full_name = :name, active = :active,
                       version = version + 1, updated_at = now()
                where id = :id and version = :version
                """)
                .param("id", id).param("email", email).param("name", fullName).param("active", active)
                .param("version", expectedVersion)
                .update();
    }

    public void setActive(UUID id, boolean active) {
        jdbc.sql("update iam.app_user set active = :active, version = version + 1, updated_at = now() where id = :id")
                .param("id", id).param("active", active).update();
    }

    public List<UserDtos.Ref> groupsOf(UUID userId) {
        return jdbc.sql("""
                select g.id, g.name from iam.user_group g join iam.group_member m on m.group_id = g.id
                where m.user_id = :u order by g.name
                """).param("u", userId)
                .query((rs, i) -> new UserDtos.Ref(rs.getObject("id", UUID.class), rs.getString("name"))).list();
    }

    public List<UserDtos.Ref> orgUnitsOf(UUID userId) {
        return jdbc.sql("""
                select o.id, o.name from iam.org_unit o join iam.org_unit_member m on m.org_unit_id = o.id
                where m.user_id = :u order by o.name
                """).param("u", userId)
                .query((rs, i) -> new UserDtos.Ref(rs.getObject("id", UUID.class), rs.getString("name"))).list();
    }

    UserDtos.User map(ResultSet rs, int i) throws SQLException {
        return new UserDtos.User(rs.getObject("id", UUID.class), rs.getString("keycloak_id"), rs.getString("username"),
                rs.getString("email"), rs.getString("full_name"), rs.getString("type"), rs.getBoolean("active"),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
