package hu.dmsone.archi.irat;

import hu.dmsone.archi.platform.web.idempotency.IdempotencyService;
import hu.dmsone.archi.platform.web.permission.PermissionService;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Publikus integrációs API v1: {@code https://api.<domain>/v1/t/{tenant}/irat/...}.
 * A létrehozó hívások {@code Idempotency-Key}-t fogadnak: egy újrapróbált kérés nem iktat kétszer.
 */
@RestController
@RequestMapping("/public/v1/t/{tenant}/irat/kuldemenyek")
public class IratPublicApiV1 {

    public record KuldemenyV1(UUID id, String allapot, LocalDate erkezesDatum, String targy, UUID partnerId,
                              String partnerNev, LocalDate bontasDatum, Integer mellekletekSzama, String iktatoszam,
                              Instant iktatasIdeje, UUID ugyintezoId, String ugyintezoNev, long version) {
        static KuldemenyV1 of(Kuldemeny k) {
            return new KuldemenyV1(k.id(), k.allapot().name(), k.erkezesDatum(), k.targy(), k.partnerId(),
                    k.partnerNev(), k.bontasDatum(), k.mellekletekSzama(), k.iktatoszam(), k.iktatasIdeje(),
                    k.ugyintezoId(), k.ugyintezoNev(), k.version());
        }
    }

    private final KuldemenyService service;
    private final IratKereso kereso;
    private final IdempotencyService idempotency;
    private final PermissionService permissions;

    public IratPublicApiV1(KuldemenyService service, IratKereso kereso, IdempotencyService idempotency,
                           PermissionService permissions) {
        this.service = service;
        this.kereso = kereso;
        this.idempotency = idempotency;
        this.permissions = permissions;
    }

    @GetMapping("/{id}")
    @RequiresPermission({"irat.read", "irat.report"})
    public ResponseEntity<KuldemenyV1> get(@PathVariable UUID id) {
        Kuldemeny k = service.get(id);
        return ResponseEntity.ok().eTag(Long.toString(k.version())).body(KuldemenyV1.of(k));
    }

    @GetMapping
    @RequiresPermission("irat.report")
    public IratKereso.Result search(@RequestParam(required = false) String iktatoszam,
                                    @RequestParam(required = false) Kuldemeny.Allapot allapot,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate erkezesTol,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate erkezesIg,
                                    @RequestParam(required = false) UUID partnerId,
                                    @RequestParam(required = false) UUID ugyintezoId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return kereso.search(new IratKereso.Filter(iktatoszam, allapot, erkezesTol, erkezesIg, null, partnerId,
                null, ugyintezoId, null, null), page, size);
    }

    @PostMapping
    @RequiresPermission("irat.erkeztetes")
    public ResponseEntity<KuldemenyV1> erkeztet(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                                @Valid @RequestBody IratDtos.ErkeztetesRequest req) {
        return idempotency.execute(key, req, KuldemenyV1.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(KuldemenyV1.of(service.erkeztet(req))));
    }

    @PostMapping("/{id}/bontas")
    @RequiresPermission("irat.bontas")
    public KuldemenyV1 bont(@PathVariable UUID id, @Valid @RequestBody IratDtos.BontasRequest req) {
        return KuldemenyV1.of(service.bont(id, req));
    }

    @PostMapping("/{id}/iktatas")
    @RequiresPermission("irat.iktatas")
    public KuldemenyV1 iktat(@PathVariable UUID id, @Valid @RequestBody IratDtos.IktatasRequest req) {
        return KuldemenyV1.of(service.iktat(id, req));
    }

    @PostMapping("/egylepeses")
    public ResponseEntity<KuldemenyV1> egylepeses(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                                  @Valid @RequestBody IratDtos.EgylepesesRequest req) {
        permissions.require("irat.erkeztetes");
        permissions.require("irat.bontas");
        permissions.require("irat.iktatas");
        return idempotency.execute(key, req, KuldemenyV1.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(KuldemenyV1.of(service.egylepeses(req))));
    }
}
