package hu.dmsone.archi.platform.web.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A metódus csak akkor futhat, ha a hívónak a felsorolt jogosultsági elemek közül legalább egy megvan.
 * A jogosultságok forrása az IAM szolgáltatás (nem a felhő IAM és nem a Keycloak szerepkörei).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String[] value();
}
