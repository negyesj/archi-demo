package hu.dmsone.archi.platform.web.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.security.EffectivePermissions;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.web.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.time.Duration;
import java.util.Arrays;

/**
 * Effektív jogosultságok lekérése Redis cache-en át. Az IAM minden jogosultság- vagy tagságváltozáskor
 * törli a tenant kulcsait ({@code perm:<tenant>:*}), így egy jogelvétel azonnal hat.
 */
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private static final String REQUEST_ATTR = PermissionService.class.getName() + ".current";

    private final PermissionSource source;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public PermissionService(PermissionSource source, StringRedisTemplate redis, ObjectMapper objectMapper, Duration ttl) {
        this.source = source;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public static String cacheKey(String tenant, String subject) {
        return "perm:" + tenant + ":" + subject;
    }

    public EffectivePermissions current() {
        String subject = CurrentUser.subject();
        if (subject == null) {
            return EffectivePermissions.none(null);
        }
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null && attrs.getAttribute(REQUEST_ATTR, RequestAttributes.SCOPE_REQUEST) instanceof EffectivePermissions p) {
            return p;
        }
        EffectivePermissions p = load(TenantContext.require(), subject);
        if (attrs != null) {
            attrs.setAttribute(REQUEST_ATTR, p, RequestAttributes.SCOPE_REQUEST);
        }
        return p;
    }

    private EffectivePermissions load(String tenant, String subject) {
        String key = cacheKey(tenant, subject);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, EffectivePermissions.class);
            }
        } catch (Exception e) {
            log.warn("Jogosultság cache olvasási hiba, közvetlen lekérés", e);
        }
        EffectivePermissions p = source.load(tenant, subject);
        if (p.userId() != null) {
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(p), ttl);
            } catch (Exception e) {
                log.warn("Jogosultság cache írási hiba", e);
            }
        }
        return p;
    }

    public boolean has(String permission) {
        return current().has(permission);
    }

    public void require(String... anyOf) {
        if (!current().hasAny(anyOf)) {
            throw new AccessDeniedException("Hiányzó jogosultság: " + String.join(" vagy ", Arrays.asList(anyOf)));
        }
    }
}
