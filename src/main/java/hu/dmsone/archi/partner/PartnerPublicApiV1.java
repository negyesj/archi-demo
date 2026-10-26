package hu.dmsone.archi.partner;

import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.platform.web.idempotency.IdempotencyService;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publikus integrációs API v1: {@code https://api.<domain>/v1/t/{tenant}/partner/partners}.
 * Saját DTO; ETag / If-Match a párhuzamos módosítás ellen; Idempotency-Key a létrehozáshoz.
 */
@RestController
@RequestMapping("/public/v1/t/{tenant}/partner/partners")
public class PartnerPublicApiV1 {

    public record PartnerV1(UUID id, String name, String taxNumber, String registrationNumber, String country,
                            String postalCode, String city, String address, String email, String phone,
                            boolean active, long version, Instant updatedAt) {
        static PartnerV1 of(Partner p) {
            return new PartnerV1(p.id(), p.name(), p.taxNumber(), p.registrationNumber(), p.country(), p.postalCode(),
                    p.city(), p.address(), p.email(), p.phone(), p.active(), p.version(), p.updatedAt());
        }
    }

    public record PageV1(List<PartnerV1> items, long total, int page, int size) { }

    private final PartnerService service;
    private final IdempotencyService idempotency;

    public PartnerPublicApiV1(PartnerService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @GetMapping
    @RequiresPermission("partner.read")
    public PageV1 list(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean active,
                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        PartnerService.Page p = service.search(q, active, page, size);
        return new PageV1(p.items().stream().map(PartnerV1::of).toList(), p.total(), p.page(), p.size());
    }

    @GetMapping("/{id}")
    @RequiresPermission("partner.read")
    public ResponseEntity<PartnerV1> get(@PathVariable UUID id) {
        Partner p = service.get(id);
        return ResponseEntity.ok().eTag(Long.toString(p.version())).body(PartnerV1.of(p));
    }

    @PostMapping
    @RequiresPermission("partner.write")
    public ResponseEntity<PartnerV1> create(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                            @Valid @RequestBody Partner.Save req) {
        return idempotency.execute(key, req, PartnerV1.class, () -> {
            Partner p = service.create(req);
            return ResponseEntity.status(HttpStatus.CREATED).eTag(Long.toString(p.version())).body(PartnerV1.of(p));
        });
    }

    @PutMapping("/{id}")
    @RequiresPermission("partner.write")
    public ResponseEntity<PartnerV1> update(@PathVariable UUID id,
                                            @RequestHeader(name = "If-Match", required = false) String ifMatch,
                                            @Valid @RequestBody Partner.Save req) {
        long version = version(ifMatch, req.version());
        Partner p = service.update(id, req, version);
        return ResponseEntity.ok().eTag(Long.toString(p.version())).body(PartnerV1.of(p));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("partner.write")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestHeader(name = "If-Match", required = false) String ifMatch) {
        service.delete(id, ifMatch == null ? null : version(ifMatch, null));
        return ResponseEntity.noContent().build();
    }

    static long version(String ifMatch, Long bodyVersion) {
        if (ifMatch != null && !ifMatch.isBlank()) {
            try {
                return Long.parseLong(ifMatch.replace("W/", "").replace("\"", "").trim());
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Érvénytelen If-Match fejléc");
            }
        }
        if (bodyVersion != null) {
            return bodyVersion;
        }
        throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "If-Match szükséges",
                "Módosításhoz az If-Match fejléc (ETag) megadása kötelező");
    }
}
