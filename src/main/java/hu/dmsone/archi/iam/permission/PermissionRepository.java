package hu.dmsone.archi.iam.permission;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class PermissionRepository {

    public record Permission(String code, String serviceId, String name, String description, boolean active) { }

    public record Grant(UUID id, String permissionCode, String permissionName, String subjectType, UUID subjectId,
                        String subjectName, java.time.Instant createdAt, String createdBy) { }

    private final JdbcClient jdbc;

    public PermissionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Permission> listActive() {
        return jdbc.sql("select * from iam.permission where active order by service_id, code")
                .query((rs, i) -> new Permission(rs.getString("code"), rs.getString("service_id"), rs.getString("name"),
                        rs.getString("description"), rs.getBoolean("active"))).list();
    }

    public void upsert(String code, String serviceId, String name, String description, boolean active) {
        jdbc.sql("""
                insert into iam.permission (code, service_id, name, description, active)
                values (:code, :service, :name, :d, :active)
                on conflict (code) do update set service_id = excluded.service_id, name = excluded.name,
                    description = excluded.description, active = excluded.active
                """).param("code", code).param("service", serviceId).param("name", name).param("d", description)
                .param("active", active).update();
    }

    public void deactivateServicesExcept(Set<String> enabledServices) {
        if (enabledServices.isEmpty()) {
            jdbc.sql("update iam.permission set active = false").update();
            return;
        }
        jdbc.sql("update iam.permission set active = false where service_id not in (:s)")
                .param("s", List.copyOf(enabledServices)).update();
    }

    public List<Grant> grants(String subjectType, UUID subjectId, String permissionCode) {
        StringBuilder sql = new StringBuilder("""
                select g.*, p.name as permission_name,
                       case when g.subject_type = 'USER' then (select u.full_name from iam.app_user u where u.id = g.subject_id)
                            else (select gr.name from iam.user_group gr where gr.id = g.subject_id) end as subject_name
                from iam.permission_grant g join iam.permission p on p.code = g.permission_code
                where p.active
                """);
        if (subjectType != null) {
            sql.append(" and g.subject_type = :st");
        }
        if (subjectId != null) {
            sql.append(" and g.subject_id = :sid");
        }
        if (permissionCode != null) {
            sql.append(" and g.permission_code = :pc");
        }
        sql.append(" order by g.permission_code");
        return jdbc.sql(sql.toString()).param("st", subjectType).param("sid", subjectId).param("pc", permissionCode)
                .query((rs, i) -> new Grant(rs.getObject("id", UUID.class), rs.getString("permission_code"),
                        rs.getString("permission_name"), rs.getString("subject_type"), rs.getObject("subject_id", UUID.class),
                        rs.getString("subject_name"), rs.getTimestamp("created_at").toInstant(), rs.getString("created_by")))
                .list();
    }

    public boolean permissionActive(String code) {
        return jdbc.sql("select count(*) from iam.permission where code = :c and active").param("c", code)
                .query(Long.class).single() > 0;
    }

    public int insertGrant(UUID id, String code, String subjectType, UUID subjectId, String createdBy) {
        return jdbc.sql("""
                insert into iam.permission_grant (id, permission_code, subject_type, subject_id, created_by)
                values (:id, :code, :st, :sid, :by) on conflict do nothing
                """).param("id", id).param("code", code).param("st", subjectType).param("sid", subjectId)
                .param("by", createdBy).update();
    }

    public void grantAllActiveTo(UUID groupId) {
        jdbc.sql("""
                insert into iam.permission_grant (id, permission_code, subject_type, subject_id, created_by)
                select gen_random_uuid(), p.code, 'GROUP', :g, 'system' from iam.permission p where p.active
                on conflict do nothing
                """).param("g", groupId).update();
    }

    public int deleteGrant(UUID id) {
        return jdbc.sql("delete from iam.permission_grant where id = :id").param("id", id).update();
    }

    public Set<String> effectivePermissions(UUID userId) {
        return new HashSet<>(jdbc.sql("""
                select distinct p.code from iam.permission p
                join iam.permission_grant g on g.permission_code = p.code
                where p.active and (
                    (g.subject_type = 'USER' and g.subject_id = :u)
                 or (g.subject_type = 'GROUP' and g.subject_id in (select m.group_id from iam.group_member m where m.user_id = :u)))
                """).param("u", userId).query(String.class).list());
    }

    public Set<String> orgUnitIds(UUID userId) {
        return new HashSet<>(jdbc.sql("select org_unit_id::text from iam.org_unit_member where user_id = :u")
                .param("u", userId).query(String.class).list());
    }

    public Set<String> groupIds(UUID userId) {
        return new HashSet<>(jdbc.sql("select group_id::text from iam.group_member where user_id = :u")
                .param("u", userId).query(String.class).list());
    }
}
