package hu.dmsone.archi.tenantadmin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.web.security.CurrentUser;
import hu.dmsone.archi.tenantadmin.adminuser.AdminUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Az admin API-t csak a {@code platform} realm aktív admin felhasználói érhetik el.
 * SUPPORT szerepkör: olvasás és loglevel állítás; PLATFORM_ADMIN: minden.
 */
@Component
public class AdminGuard implements HandlerInterceptor {

    public static final String ATTR = AdminGuard.class.getName() + ".user";
    public static final String PLATFORM_REALM = "platform";

    private final AdminUserRepository users;
    private final ObjectMapper objectMapper;

    public AdminGuard(AdminUserRepository users, ObjectMapper objectMapper) {
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!CurrentUser.realm().map(PLATFORM_REALM::equals).orElse(false)) {
            return deny(response, "Az admin felület csak platform felhasználóknak érhető el.");
        }
        Optional<AdminUser> user = users.findByUsername(CurrentUser.username()).filter(AdminUser::active);
        if (user.isEmpty()) {
            return deny(response, "Nincs admin jogosultság.");
        }
        boolean readOnly = "GET".equals(request.getMethod());
        boolean logLevel = request.getRequestURI().startsWith("/api/admin/loglevels");
        if (!readOnly && !logLevel && !user.get().isPlatformAdmin()) {
            return deny(response, "A művelethez platform admin szerepkör szükséges.");
        }
        request.setAttribute(ATTR, user.get());
        MDC.put("userId", user.get().username());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove("userId");
    }

    public static AdminUser current(HttpServletRequest request) {
        return (AdminUser) request.getAttribute(ATTR);
    }

    private boolean deny(HttpServletResponse response, String detail) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Nincs jogosultság");
        body.put("status", 403);
        body.put("detail", detail);
        body.put("traceId", MDC.get("traceId"));
        response.setStatus(403);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
        return false;
    }
}
