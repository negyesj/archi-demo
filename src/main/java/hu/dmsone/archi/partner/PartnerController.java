package hu.dmsone.archi.partner;

import hu.dmsone.archi.platform.web.error.ApiException;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** A felhasználói felület belső API-ja. */
@RestController
@RequestMapping("/api/t/{tenant}/partner/partners")
public class PartnerController {

    private final PartnerService service;

    public PartnerController(PartnerService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("partner.read")
    public PartnerService.Page search(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return service.search(q, active, page, size);
    }

    /** Autocomplete más szolgáltatásoknak (pl. érkeztetés): bejelentkezett felhasználó hívhatja, csak aktív partnerek. */
    @GetMapping("/lookup")
    public List<Partner.Summary> lookup(@RequestParam(defaultValue = "") String q,
                                        @RequestParam(defaultValue = "20") int limit) {
        return service.lookup(q, limit);
    }

    /** Egy partner rövid adata más szolgáltatásoknak (ellenőrzés és pillanatkép). */
    @GetMapping("/{id}/summary")
    public Partner.Summary summary(@PathVariable UUID id) {
        return PartnerService.summary(service.get(id));
    }

    @GetMapping("/{id}")
    @RequiresPermission("partner.read")
    public ResponseEntity<Partner> get(@PathVariable UUID id) {
        Partner p = service.get(id);
        return ResponseEntity.ok().eTag(Long.toString(p.version())).body(p);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("partner.write")
    public Partner create(@Valid @RequestBody Partner.Save req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @RequiresPermission("partner.write")
    public Partner update(@PathVariable UUID id, @Valid @RequestBody Partner.Save req) {
        if (req.version() == null) {
            throw ApiException.badRequest("A verzió megadása kötelező");
        }
        return service.update(id, req, req.version());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("partner.write")
    public void delete(@PathVariable UUID id, @RequestParam(required = false) Long version) {
        service.delete(id, version);
    }
}
