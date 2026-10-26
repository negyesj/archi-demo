package hu.dmsone.archi.audit;

import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class AuditController {

    private final AuditQuery query;

    public AuditController(AuditQuery query) {
        this.query = query;
    }

    @GetMapping({"/api/t/{tenant}/audit/events", "/public/v1/t/{tenant}/audit/events"})
    @RequiresPermission("audit.read")
    public AuditQuery.Page search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return query.search(new AuditQuery.Filter(from, to, service, entity, entityId, actor, action, q), page, size);
    }

    /** Egy entitás teljes élettörténete. */
    @GetMapping("/api/t/{tenant}/audit/entities/{entity}/{entityId}")
    @RequiresPermission("audit.read")
    public AuditQuery.Page history(@PathVariable String entity, @PathVariable String entityId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "100") int size) {
        return query.search(new AuditQuery.Filter(null, null, null, entity, entityId, null, null, null), page, size);
    }
}
