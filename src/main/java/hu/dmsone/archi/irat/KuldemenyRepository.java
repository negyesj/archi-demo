package hu.dmsone.archi.irat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class KuldemenyRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public KuldemenyRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<Kuldemeny> find(UUID id) {
        return jdbc.sql("select * from irat.kuldemeny where id = :id").param("id", id).query(this::map).optional();
    }

    public List<Kuldemeny> list(Kuldemeny.Allapot allapot, int limit) {
        return jdbc.sql("""
                select * from irat.kuldemeny where (cast(:a as text) is null or allapot = cast(:a as text))
                order by erkezes_datum desc, created_at desc limit :l
                """).param("a", allapot == null ? null : allapot.name()).param("l", limit).query(this::map).list();
    }

    public List<String> aclOrgUnits(UUID id) {
        String json = jdbc.sql("select acl_org_units::text from irat.kuldemeny where id = :id").param("id", id)
                .query(String.class).single();
        try {
            return List.of(objectMapper.readValue(json, String[].class));
        } catch (Exception e) {
            return List.of();
        }
    }

    public void insertErkeztetett(UUID id, IratDtos.ErkeztetesRequest req, String partnerNev, String partnerCim,
                                  String actorId, String actorName, List<String> aclOrgUnits) {
        jdbc.sql("""
                insert into irat.kuldemeny (id, allapot, erkezes_datum, targy, partner_id, partner_nev, partner_cim,
                    erkeztette_id, erkeztette_nev, acl_org_units)
                values (:id, 'ERKEZTETETT', :ed, :targy, :pid, :pnev, :pcim, :aid, :anev, cast(:acl as jsonb))
                """)
                .param("id", id).param("ed", Date.valueOf(req.erkezesDatum())).param("targy", req.targy())
                .param("pid", req.partnerId()).param("pnev", partnerNev).param("pcim", partnerCim)
                .param("aid", actorId).param("anev", actorName).param("acl", json(aclOrgUnits))
                .update();
    }

    public int updateBontott(UUID id, IratDtos.BontasRequest req, String actorId, String actorName, long expectedVersion) {
        return jdbc.sql("""
                update irat.kuldemeny set allapot = 'BONTOTT', bontas_datum = :bd, mellekletek_szama = :m,
                    bontotta_id = :aid, bontotta_nev = :anev, version = version + 1, updated_at = now()
                where id = :id and allapot = 'ERKEZTETETT' and version = :v
                """)
                .param("id", id).param("bd", Date.valueOf(req.bontasDatum())).param("m", req.mellekletekSzama())
                .param("aid", actorId).param("anev", actorName).param("v", expectedVersion)
                .update();
    }

    public int updateIktatott(UUID id, String konyv, int ev, long sorszam, String iktatoszam, UUID ugyintezoId,
                              String ugyintezoNev, long expectedVersion) {
        return jdbc.sql("""
                update irat.kuldemeny set allapot = 'IKTATOTT', iktato_konyv = :k, iktato_ev = :e, iktato_sorszam = :s,
                    iktatoszam = :szam, iktatas_ideje = :most, ugyintezo_id = :uid, ugyintezo_nev = :unev,
                    version = version + 1, updated_at = now()
                where id = :id and allapot = 'BONTOTT' and version = :v
                """)
                .param("id", id).param("k", konyv).param("e", ev).param("s", sorszam).param("szam", iktatoszam)
                .param("most", Timestamp.from(Instant.now())).param("uid", ugyintezoId).param("unev", ugyintezoNev)
                .param("v", expectedVersion)
                .update();
    }

    /** A következő sorszám az iktatókönyvben; a számláló sorát a tranzakció végéig zárolja. */
    public long nextSorszam(String konyv, int ev) {
        return jdbc.sql("""
                insert into irat.iktato_szamlalo (konyv, ev, utolso_sorszam) values (:k, :e, 1)
                on conflict (konyv, ev) do update set utolso_sorszam = irat.iktato_szamlalo.utolso_sorszam + 1
                returning utolso_sorszam
                """).param("k", konyv).param("e", ev).query(Long.class).single();
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o == null ? List.of() : o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Kuldemeny map(ResultSet rs, int i) throws SQLException {
        Date bd = rs.getDate("bontas_datum");
        Timestamp ii = rs.getTimestamp("iktatas_ideje");
        return new Kuldemeny(rs.getObject("id", UUID.class), Kuldemeny.Allapot.valueOf(rs.getString("allapot")),
                rs.getDate("erkezes_datum").toLocalDate(), rs.getString("targy"), rs.getObject("partner_id", UUID.class),
                rs.getString("partner_nev"), rs.getString("partner_cim"), rs.getString("erkeztette_id"),
                rs.getString("erkeztette_nev"), bd == null ? null : bd.toLocalDate(),
                (Integer) rs.getObject("mellekletek_szama"), rs.getString("bontotta_id"), rs.getString("bontotta_nev"),
                rs.getString("iktatoszam"), ii == null ? null : ii.toInstant(), rs.getObject("ugyintezo_id", UUID.class),
                rs.getString("ugyintezo_nev"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
