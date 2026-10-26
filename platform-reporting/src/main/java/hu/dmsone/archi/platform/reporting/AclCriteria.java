package hu.dmsone.archi.platform.reporting;

import hu.dmsone.archi.platform.security.EffectivePermissions;
import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Adatszintű láthatósági szűrő a riport dokumentumok {@code _acl} mezőjére. Minden riport lekérdezéshez
 * hozzá kell fűzni, kivéve ha a felhasználónak "mindent lát" jogosultsága van.
 */
public final class AclCriteria {

    public static final String FIELD = "_acl";

    public static Criteria visibleTo(EffectivePermissions p, boolean seesAll) {
        if (seesAll) {
            return new Criteria();
        }
        return new Criteria().orOperator(
                Criteria.where(FIELD + ".everyone").is(true),
                Criteria.where(FIELD + ".users").in(p.userId() == null ? "-" : p.userId()),
                Criteria.where(FIELD + ".orgUnits").in(p.orgUnits()));
    }

    private AclCriteria() { }
}
