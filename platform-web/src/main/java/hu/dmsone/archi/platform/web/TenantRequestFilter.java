package hu.dmsone.archi.platform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import hu.dmsone.archi.platform.trail.TrailKind;
import hu.dmsone.archi.platform.trail.TrailRecorder;
import hu.dmsone.archi.platform.web.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@code /api/t/{tenant}/...} és {@code /public/v1/t/{tenant}/...} kérések tenant-kontextusát állítja be,
 * és ellenőrzi: a tenant aktív, a szolgáltatás engedélyezett, a token ugyanahhoz a tenanthoz tartozik.
 */
public class TenantRequestFilter extends OncePerRequestFilter {

    private static final Pattern PATH = Pattern.compile("^/(?:api|public/v1)/t/([a-z][a-z0-9]{1,29})(?:/.*)?$");

    private final TenantRegistry registry;
    private final ObjectMapper objectMapper;
    private final String serviceId;
    private final boolean coreService;
    private final TrailRecorder trail;

    public TenantRequestFilter(TenantRegistry registry, ObjectMapper objectMapper, String serviceId, boolean coreService,
                               TrailRecorder trail) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.serviceId = serviceId;
        this.coreService = coreService;
        this.trail = trail;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            response.setHeader("X-Trace-Id", traceId);
        }
        Matcher m = PATH.matcher(request.getRequestURI());
        if (!m.matches()) {
            chain.doFilter(request, response);
            return;
        }
        String tenant = m.group(1);
        Optional<TenantInfo> info = registry.findActive(tenant);
        if (info.isEmpty()) {
            problem(response, 404, "Ismeretlen tenant", "A tenant nem létezik vagy nem aktív.");
            return;
        }
        if (!coreService && !info.get().serviceEnabled(serviceId)) {
            problem(response, 403, "Szolgáltatás nincs engedélyezve",
                    "A(z) " + serviceId + " szolgáltatás nincs engedélyezve ennél a tenantnál.");
            return;
        }
        Optional<String> realm = CurrentUser.realm();
        if (realm.isPresent() && !realm.get().equals(tenant)) {
            problem(response, 403, "Tenant eltérés", "A token másik tenanthoz tartozik.");
            return;
        }
        TrailRecorder.Handle step = trail == null || !trail.enabled() ? null : trail.begin(tenant, traceId, parentSpan(request));
        String error = null;
        try {
            TenantContext.set(tenant);
            if (CurrentUser.subject() != null) {
                MDC.put("userId", CurrentUser.subject());
            }
            if (CurrentUser.isServiceAccount()) {
                MDC.put("clientId", CurrentUser.clientId());
            }
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            error = TrailRecorder.errorText(e);
            throw e;
        } finally {
            if (step != null) {
                int status = error != null ? 500 : response.getStatus();
                String user = CurrentUser.isServiceAccount() ? CurrentUser.clientId() : CurrentUser.username();
                trail.end(step, TrailRecorder.Spec.of(TrailKind.HTTP, operation(request), serviceId).withUser(user),
                        status >= 500 ? "ERROR" : "OK", error != null ? error : "HTTP " + status);
            }
            TenantContext.clear();
            MDC.remove("userId");
            MDC.remove("clientId");
        }
    }

    /** {@code POST /api/t/{tenant}/irat/kuldemenyek/{id}/iktatas}: a Spring MVC útvonalminta, ha ismert. */
    private static String operation(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern != null ? pattern.toString()
                : request.getRequestURI().replaceAll("/[0-9a-fA-F-]{36}(?=/|$)", "/{id}").replaceAll("/t/[a-z0-9]+/", "/t/{tenant}/");
        return request.getMethod() + " " + path;
    }

    /** A hívó span azonosítója a W3C traceparent fejlécből (a trail fában ehhez kapcsolódik a lépés). */
    private static String parentSpan(HttpServletRequest request) {
        String[] ids = TrailRecorder.traceparent(request.getHeader("traceparent"));
        return ids == null ? null : ids[1];
    }

    private void problem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("traceId", MDC.get("traceId"));
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
