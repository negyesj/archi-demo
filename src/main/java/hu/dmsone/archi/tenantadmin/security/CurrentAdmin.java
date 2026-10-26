package hu.dmsone.archi.tenantadmin.security;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Az aktuális admin felhasználó (a kérésből), vagy {@code system}, ha háttérfolyamat hív. */
public final class CurrentAdmin {

    public static AdminUser get() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return (AdminUser) attrs.getAttribute(AdminGuard.ATTR, RequestAttributes.SCOPE_REQUEST);
    }

    public static String username() {
        AdminUser u = get();
        return u == null ? "system" : u.username();
    }

    public static String displayName() {
        AdminUser u = get();
        return u == null ? "Rendszer" : u.fullName();
    }

    private CurrentAdmin() { }
}
