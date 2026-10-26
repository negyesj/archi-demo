package hu.dmsone.archi.platform.tenant;

import java.util.regex.Pattern;

/** A tenanthoz tartozó erőforrások elnevezési szabályai. Egy helyen, hogy minden komponens ugyanazt használja. */
public final class TenantNames {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9]{1,29}$");

    public static boolean isValidCode(String code) {
        return code != null && CODE.matcher(code).matches();
    }

    public static String requireValid(String code) {
        if (!isValidCode(code)) {
            throw new IllegalArgumentException("Érvénytelen tenant kód: " + code);
        }
        return code;
    }

    public static String pgDatabase(String code) { return "t_" + requireValid(code); }
    public static String pgUser(String code) { return "t_" + requireValid(code) + "_app"; }
    public static String mongoDatabase(String code) { return "rpt_" + requireValid(code); }
    public static String mongoUser(String code) { return "rpt_" + requireValid(code) + "_app"; }
    public static String pgSecretKey(String code) { return "tenant/" + requireValid(code) + "/pg"; }
    public static String mongoSecretKey(String code) { return "tenant/" + requireValid(code) + "/mongo"; }
    /** A tenant Keycloak realmje megegyezik a tenant kóddal. */
    public static String realm(String code) { return requireValid(code); }

    private TenantNames() { }
}
