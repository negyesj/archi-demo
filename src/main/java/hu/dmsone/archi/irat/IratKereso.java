package hu.dmsone.archi.irat;

import hu.dmsone.archi.platform.reporting.AclCriteria;
import hu.dmsone.archi.platform.reporting.TenantMongoTemplates;
import hu.dmsone.archi.platform.security.EffectivePermissions;
import hu.dmsone.archi.platform.web.permission.PermissionService;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Iratkereső: a NoSQL (MongoDB) read modellből ({@code rpt_<tenant>.irat_kuldemeny}) szolgál ki, jogosultságkezelten.
 * A dokumentumokat a reporting-projector tölti a {@code domain.irat.events} eseményekből.
 */
@Component
public class IratKereso {

    public static final String COLLECTION = "irat_kuldemeny";

    public record Filter(String iktatoszam, Kuldemeny.Allapot allapot, LocalDate erkezesTol, LocalDate erkezesIg,
                         String partner, UUID partnerId, String ugyintezo, UUID ugyintezoId, String targy,
                         Integer mellekletekSzama) { }

    public record Result(List<Map<String, Object>> items, long total, int page, int size) { }

    private final TenantMongoTemplates mongo;
    private final PermissionService permissions;

    public IratKereso(TenantMongoTemplates mongo, PermissionService permissions) {
        this.mongo = mongo;
        this.permissions = permissions;
    }

    public Result search(Filter f, int page, int size) {
        int s = Math.min(Math.max(size, 1), 200);
        int p = Math.max(page, 0);
        List<Criteria> and = new ArrayList<>();
        EffectivePermissions me = permissions.current();
        and.add(AclCriteria.visibleTo(me, me.has("irat.report.all")));
        if (notBlank(f.iktatoszam())) { and.add(Criteria.where("iktatoszam").regex(Pattern.quote(f.iktatoszam()), "i")); }
        if (f.allapot() != null) { and.add(Criteria.where("allapot").is(f.allapot().name())); }
        if (f.erkezesTol() != null) { and.add(Criteria.where("erkezesDatum").gte(f.erkezesTol().toString())); }
        if (f.erkezesIg() != null) { and.add(Criteria.where("erkezesDatum").lte(f.erkezesIg().toString())); }
        if (notBlank(f.partner())) { and.add(Criteria.where("partnerNev").regex(Pattern.quote(f.partner()), "i")); }
        if (f.partnerId() != null) { and.add(Criteria.where("partnerId").is(f.partnerId().toString())); }
        if (notBlank(f.ugyintezo())) { and.add(Criteria.where("ugyintezoNev").regex(Pattern.quote(f.ugyintezo()), "i")); }
        if (f.ugyintezoId() != null) { and.add(Criteria.where("ugyintezoId").is(f.ugyintezoId().toString())); }
        if (notBlank(f.targy())) { and.add(Criteria.where("targy").regex(Pattern.quote(f.targy()), "i")); }
        if (f.mellekletekSzama() != null) { and.add(Criteria.where("mellekletekSzama").is(f.mellekletekSzama())); }
        Query q = new Query(new Criteria().andOperator(and.toArray(new Criteria[0])));
        MongoTemplate t = mongo.current();
        long total = t.count(q, COLLECTION);
        q.with(Sort.by(Sort.Direction.DESC, "erkezesDatum", "createdAt")).skip((long) p * s).limit(s);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Document d : t.find(q, Document.class, COLLECTION)) {
            d.remove(AclCriteria.FIELD);
            d.remove("_id");
            items.add(d);
        }
        return new Result(items, total, p, s);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
