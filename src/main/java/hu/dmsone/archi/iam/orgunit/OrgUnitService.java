package hu.dmsone.archi.iam.orgunit;

import hu.dmsone.archi.iam.common.IamChanges;
import hu.dmsone.archi.iam.user.UserDtos;
import hu.dmsone.archi.iam.user.UserRepository;
import hu.dmsone.archi.platform.web.error.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Szervezeti egységek (fa szerkezet) és a felhasználók egységhez rendelése. */
@Service
public class OrgUnitService {

    public record OrgUnit(UUID id, String code, String name, UUID parentId, long version, int memberCount,
                          Instant createdAt, Instant updatedAt) { }

    public record SaveOrgUnit(@NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
                              @NotBlank @Size(max = 200) String name, UUID parentId, Long version) { }

    private final JdbcClient jdbc;
    private final IamChanges changes;
    private final UserRepository users;

    public OrgUnitService(JdbcClient jdbc, IamChanges changes, UserRepository users) {
        this.jdbc = jdbc;
        this.changes = changes;
        this.users = users;
    }

    private static final String SELECT = """
            select o.*, (select count(*) from iam.org_unit_member m where m.org_unit_id = o.id) as member_count
            from iam.org_unit o
            """;

    public List<OrgUnit> list() {
        return jdbc.sql(SELECT + " order by o.name").query(this::map).list();
    }

    public OrgUnit get(UUID id) {
        return jdbc.sql(SELECT + " where o.id = :id").param("id", id).query(this::map).optional()
                .orElseThrow(() -> ApiException.notFound("Szervezeti egység"));
    }

    @Transactional
    public OrgUnit create(SaveOrgUnit req) {
        if (req.parentId() != null) {
            get(req.parentId());
        }
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into iam.org_unit (id, code, name, parent_id) values (:id, :code, :name, :p)")
                .param("id", id).param("code", req.code()).param("name", req.name()).param("p", req.parentId())
                .update();
        OrgUnit o = get(id);
        changes.record("OrgUnit", id.toString(), "CREATE", o.version(), null, o, false);
        return o;
    }

    @Transactional
    public OrgUnit update(UUID id, SaveOrgUnit req) {
        OrgUnit before = get(id);
        if (req.parentId() != null) {
            ensureNoCycle(id, req.parentId());
        }
        int n = jdbc.sql("""
                update iam.org_unit set code = :code, name = :name, parent_id = :p, version = version + 1,
                       updated_at = now() where id = :id and (cast(:v as bigint) is null or version = cast(:v as bigint))
                """).param("id", id).param("code", req.code()).param("name", req.name())
                .param("p", req.parentId()).param("v", req.version()).update();
        if (n == 0) {
            throw ApiException.conflict("A szervezeti egységet közben más módosította, töltse újra.");
        }
        OrgUnit after = get(id);
        changes.record("OrgUnit", id.toString(), "UPDATE", after.version(), before, after, false);
        return after;
    }

    @Transactional
    public void delete(UUID id) {
        OrgUnit before = get(id);
        long children = jdbc.sql("select count(*) from iam.org_unit where parent_id = :id").param("id", id)
                .query(Long.class).single();
        if (children > 0) {
            throw ApiException.conflict("A szervezeti egységnek vannak alegységei, előbb azokat kell törölni vagy áthelyezni.");
        }
        jdbc.sql("delete from iam.org_unit where id = :id").param("id", id).update();
        changes.record("OrgUnit", id.toString(), "DELETE", before.version() + 1, before, null, true);
    }

    public List<UserDtos.UserSummary> members(UUID id) {
        get(id);
        return jdbc.sql("""
                select u.id, u.username, u.full_name, u.email, u.active from iam.app_user u
                join iam.org_unit_member m on m.user_id = u.id where m.org_unit_id = :o order by u.full_name
                """).param("o", id).query((rs, i) -> new UserDtos.UserSummary(rs.getObject("id", UUID.class),
                rs.getString("username"), rs.getString("full_name"), rs.getString("email"), rs.getBoolean("active"))).list();
    }

    @Transactional
    public void addMember(UUID orgUnitId, UUID userId) {
        get(orgUnitId);
        users.find(userId).orElseThrow(() -> ApiException.notFound("Felhasználó"));
        int n = jdbc.sql("insert into iam.org_unit_member (org_unit_id, user_id) values (:o, :u) on conflict do nothing")
                .param("o", orgUnitId).param("u", userId).update();
        if (n > 0) {
            changes.record("OrgUnitMember", orgUnitId + ":" + userId, "ASSIGN", System.currentTimeMillis(), null,
                    Map.of("orgUnitId", orgUnitId, "userId", userId), true);
        }
    }

    @Transactional
    public void removeMember(UUID orgUnitId, UUID userId) {
        int n = jdbc.sql("delete from iam.org_unit_member where org_unit_id = :o and user_id = :u")
                .param("o", orgUnitId).param("u", userId).update();
        if (n > 0) {
            changes.record("OrgUnitMember", orgUnitId + ":" + userId, "UNASSIGN", System.currentTimeMillis(),
                    Map.of("orgUnitId", orgUnitId, "userId", userId), null, true);
        }
    }

    private void ensureNoCycle(UUID id, UUID newParent) {
        UUID cursor = newParent;
        int guard = 0;
        while (cursor != null && guard++ < 1000) {
            if (Objects.equals(cursor, id)) {
                throw ApiException.badRequest("Egy szervezeti egység nem lehet a saját alegysége");
            }
            cursor = get(cursor).parentId();
        }
    }

    private OrgUnit map(ResultSet rs, int i) throws SQLException {
        return new OrgUnit(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getObject("parent_id", UUID.class), rs.getLong("version"), rs.getInt("member_count"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
