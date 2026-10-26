package hu.dmsone.archi.iam.group;

import hu.dmsone.archi.iam.common.IamChanges;
import hu.dmsone.archi.iam.user.UserDtos;
import hu.dmsone.archi.iam.user.UserRepository;
import hu.dmsone.archi.platform.web.error.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Felhasználócsoportok és csoporttagság. */
@Service
public class GroupService {

    public record Group(UUID id, String name, String description, boolean system, long version, int memberCount,
                        Instant createdAt, Instant updatedAt) { }

    public record SaveGroup(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description, Long version) { }

    private final JdbcClient jdbc;
    private final IamChanges changes;
    private final UserRepository users;

    public GroupService(JdbcClient jdbc, IamChanges changes, UserRepository users) {
        this.jdbc = jdbc;
        this.changes = changes;
        this.users = users;
    }

    public List<Group> list(String q) {
        return jdbc.sql("""
                select g.*, (select count(*) from iam.group_member m where m.group_id = g.id) as member_count
                from iam.user_group g where (cast(:q as text) is null or lower(g.name) like cast(:q as text)) order by g.name
                """).param("q", q == null || q.isBlank() ? null : "%" + q.toLowerCase() + "%")
                .query(this::map).list();
    }

    public Group get(UUID id) {
        return jdbc.sql("""
                select g.*, (select count(*) from iam.group_member m where m.group_id = g.id) as member_count
                from iam.user_group g where g.id = :id
                """).param("id", id).query(this::map).optional()
                .orElseThrow(() -> ApiException.notFound("Csoport"));
    }

    public Group findSystemAdminGroup() {
        return jdbc.sql("""
                select g.*, 0 as member_count from iam.user_group g where g.system = true order by g.created_at limit 1
                """).query(this::map).optional().orElse(null);
    }

    @Transactional
    public Group create(SaveGroup req) {
        return createInternal(req.name(), req.description(), false);
    }

    @Transactional
    public Group createInternal(String name, String description, boolean system) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into iam.user_group (id, name, description, system) values (:id, :name, :d, :s)")
                .param("id", id).param("name", name).param("d", description).param("s", system).update();
        Group g = get(id);
        changes.record("Group", id.toString(), "CREATE", g.version(), null, g, false);
        return g;
    }

    @Transactional
    public Group update(UUID id, SaveGroup req) {
        Group before = get(id);
        int n = jdbc.sql("""
                update iam.user_group set name = :name, description = :d, version = version + 1, updated_at = now()
                where id = :id and (cast(:v as bigint) is null or version = cast(:v as bigint))
                """).param("id", id).param("name", req.name()).param("d", req.description())
                .param("v", req.version()).update();
        if (n == 0) {
            throw ApiException.conflict("A csoportot közben más módosította, töltse újra.");
        }
        Group after = get(id);
        changes.record("Group", id.toString(), "UPDATE", after.version(), before, after, false);
        return after;
    }

    /** Törléskor a tagságok és a csoport jogosultságai is törlődnek. */
    @Transactional
    public void delete(UUID id) {
        Group before = get(id);
        if (before.system()) {
            throw ApiException.conflict("A rendszer admin csoport nem törölhető");
        }
        jdbc.sql("delete from iam.permission_grant where subject_type = 'GROUP' and subject_id = :id").param("id", id).update();
        jdbc.sql("delete from iam.user_group where id = :id").param("id", id).update();
        changes.record("Group", id.toString(), "DELETE", before.version() + 1, before, null, true);
    }

    public List<UserDtos.UserSummary> members(UUID groupId) {
        get(groupId);
        return jdbc.sql("""
                select u.id, u.username, u.full_name, u.email, u.active from iam.app_user u
                join iam.group_member m on m.user_id = u.id where m.group_id = :g order by u.full_name
                """).param("g", groupId).query((rs, i) -> new UserDtos.UserSummary(rs.getObject("id", UUID.class),
                rs.getString("username"), rs.getString("full_name"), rs.getString("email"), rs.getBoolean("active"))).list();
    }

    @Transactional
    public void addMember(UUID groupId, UUID userId) {
        get(groupId);
        users.find(userId).orElseThrow(() -> ApiException.notFound("Felhasználó"));
        int n = jdbc.sql("insert into iam.group_member (group_id, user_id) values (:g, :u) on conflict do nothing")
                .param("g", groupId).param("u", userId).update();
        if (n > 0) {
            changes.record("GroupMember", groupId + ":" + userId, "ASSIGN", System.currentTimeMillis(), null,
                    Map.of("groupId", groupId, "userId", userId), true);
        }
    }

    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        int n = jdbc.sql("delete from iam.group_member where group_id = :g and user_id = :u")
                .param("g", groupId).param("u", userId).update();
        if (n > 0) {
            changes.record("GroupMember", groupId + ":" + userId, "UNASSIGN", System.currentTimeMillis(),
                    Map.of("groupId", groupId, "userId", userId), null, true);
        }
    }

    private Group map(ResultSet rs, int i) throws SQLException {
        return new Group(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                rs.getBoolean("system"), rs.getLong("version"), rs.getInt("member_count"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
