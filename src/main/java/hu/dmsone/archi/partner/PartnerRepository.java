package hu.dmsone.archi.partner;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PartnerRepository {

    private final JdbcClient jdbc;

    public PartnerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Partner> find(UUID id) {
        return jdbc.sql("select * from partner.partner where id = :id").param("id", id).query(this::map).optional();
    }

    public List<Partner> search(String q, Boolean active, int limit, int offset) {
        return jdbc.sql("select * from partner.partner where " + where(q, active) + " order by name limit :l offset :o")
                .param("q", like(q)).param("qExact", q).param("active", active).param("l", limit).param("o", offset)
                .query(this::map).list();
    }

    public long count(String q, Boolean active) {
        return jdbc.sql("select count(*) from partner.partner where " + where(q, active))
                .param("q", like(q)).param("qExact", q).param("active", active).query(Long.class).single();
    }

    private static String where(String q, Boolean active) {
        StringBuilder w = new StringBuilder("1=1");
        if (q != null && !q.isBlank()) {
            w.append(" and (lower(name) like :q or tax_number = :qExact or lower(coalesce(city,'')) like :q)");
        }
        if (active != null) {
            w.append(" and active = :active");
        }
        return w.toString();
    }

    private static String like(String q) {
        return q == null ? null : "%" + q.trim().toLowerCase() + "%";
    }

    public void insert(UUID id, Partner.Save s) {
        jdbc.sql("""
                insert into partner.partner (id, name, tax_number, registration_number, country, postal_code, city,
                    address, email, phone)
                values (:id, :name, :tax, :reg, :country, :pc, :city, :address, :email, :phone)
                """)
                .param("id", id).param("name", s.name()).param("tax", s.taxNumber()).param("reg", s.registrationNumber())
                .param("country", s.country() == null ? "HU" : s.country()).param("pc", s.postalCode())
                .param("city", s.city()).param("address", s.address()).param("email", s.email()).param("phone", s.phone())
                .update();
    }

    public int update(UUID id, Partner.Save s, long expectedVersion) {
        return jdbc.sql("""
                update partner.partner set name = :name, tax_number = :tax, registration_number = :reg,
                    country = :country, postal_code = :pc, city = :city, address = :address, email = :email,
                    phone = :phone, version = version + 1, updated_at = now()
                where id = :id and version = :v and active
                """)
                .param("id", id).param("name", s.name()).param("tax", s.taxNumber()).param("reg", s.registrationNumber())
                .param("country", s.country() == null ? "HU" : s.country()).param("pc", s.postalCode())
                .param("city", s.city()).param("address", s.address()).param("email", s.email()).param("phone", s.phone())
                .param("v", expectedVersion)
                .update();
    }

    public int deactivate(UUID id, long expectedVersion) {
        return jdbc.sql("""
                update partner.partner set active = false, version = version + 1, updated_at = now()
                where id = :id and version = :v and active
                """).param("id", id).param("v", expectedVersion).update();
    }

    private Partner map(ResultSet rs, int i) throws SQLException {
        return new Partner(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("tax_number"),
                rs.getString("registration_number"), rs.getString("country"), rs.getString("postal_code"),
                rs.getString("city"), rs.getString("address"), rs.getString("email"), rs.getString("phone"),
                rs.getBoolean("active"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
