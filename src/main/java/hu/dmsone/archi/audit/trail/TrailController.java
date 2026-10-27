package hu.dmsone.archi.audit.trail;

import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Műveleti trail a tenant felületén: melyik művelet mely szolgáltatásokat, adatbázisokat, Kafka topicokat érintette,
 * az egyes lépések mennyi ideig tartottak, és órás/napi átlagok műveletenként.
 */
@RestController
public class TrailController {

    private final TrailQuery query;

    public TrailController(TrailQuery query) {
        this.query = query;
    }

    @GetMapping("/api/t/{tenant}/audit/trail/operations")
    @RequiresPermission("trail.read")
    public TrailQuery.Page<TrailQuery.Operation> operations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) Long minMs,
            @RequestParam(defaultValue = "false") boolean errorsOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Instant f = TrailQuery.orDefault(from, Duration.ofHours(24));
        Instant t = to != null ? to : Instant.now().plusSeconds(60);
        return query.operations(new TrailQuery.OperationFilter(f, t, service, q, user, minMs, errorsOnly),
                Math.max(page, 0), Math.min(Math.max(size, 1), 200));
    }

    @GetMapping("/api/t/{tenant}/audit/trail/operations/{traceId}")
    @RequiresPermission("trail.read")
    public TrailQuery.Detail detail(@PathVariable String traceId) {
        TrailQuery.Detail d = query.detail(traceId);
        if (d == null) {
            throw ApiException.notFound("A művelet");
        }
        return d;
    }

    @GetMapping("/api/t/{tenant}/audit/trail/stats")
    @RequiresPermission("trail.read")
    public List<TrailQuery.StatRow> stats(
            @RequestParam(defaultValue = "hour") String bucket,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1000") int limit) {
        Instant f = TrailQuery.orDefault(from, "day".equals(bucket) ? Duration.ofDays(30) : Duration.ofHours(24));
        Instant t = to != null ? to : Instant.now().plusSeconds(3600);
        return query.stats(bucket, f, t, service, kind, q, limit);
    }

    @GetMapping("/api/t/{tenant}/audit/trail/facets")
    @RequiresPermission("trail.read")
    public Map<String, List<String>> facets(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return query.facets(TrailQuery.orDefault(from, Duration.ofDays(30)), to != null ? to : Instant.now().plusSeconds(3600));
    }
}
