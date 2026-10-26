package hu.dmsone.archi.irat;

import hu.dmsone.archi.platform.web.permission.PermissionService;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A felhasználói felület belső API-ja. */
@RestController
@RequestMapping("/api/t/{tenant}/irat")
public class KuldemenyController {

    private final KuldemenyService service;
    private final IratKereso kereso;
    private final PermissionService permissions;

    public KuldemenyController(KuldemenyService service, IratKereso kereso, PermissionService permissions) {
        this.service = service;
        this.kereso = kereso;
        this.permissions = permissions;
    }

    /** Munkalista a feldolgozó felülethez (tranzakciós adatbázisból), pl. bontásra / iktatásra váró küldemények. */
    @GetMapping("/kuldemenyek")
    @RequiresPermission({"irat.read", "irat.bontas", "irat.iktatas"})
    public List<Kuldemeny> list(@RequestParam(required = false) Kuldemeny.Allapot allapot,
                                @RequestParam(defaultValue = "100") int limit) {
        return service.list(allapot, limit);
    }

    @GetMapping("/kuldemenyek/{id}")
    @RequiresPermission({"irat.read", "irat.bontas", "irat.iktatas", "irat.erkeztetes"})
    public Kuldemeny get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/kuldemenyek")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("irat.erkeztetes")
    public Kuldemeny erkeztet(@Valid @RequestBody IratDtos.ErkeztetesRequest req) {
        return service.erkeztet(req);
    }

    @PostMapping("/kuldemenyek/{id}/bontas")
    @RequiresPermission("irat.bontas")
    public Kuldemeny bont(@PathVariable UUID id, @Valid @RequestBody IratDtos.BontasRequest req) {
        return service.bont(id, req);
    }

    @PostMapping("/kuldemenyek/{id}/iktatas")
    @RequiresPermission("irat.iktatas")
    public Kuldemeny iktat(@PathVariable UUID id, @Valid @RequestBody IratDtos.IktatasRequest req) {
        return service.iktat(id, req);
    }

    @PostMapping("/kuldemenyek/egylepeses")
    @ResponseStatus(HttpStatus.CREATED)
    public Kuldemeny egylepeses(@Valid @RequestBody IratDtos.EgylepesesRequest req) {
        requireAll();
        return service.egylepeses(req);
    }

    /** Iratkereső a NoSQL read modellből. */
    @GetMapping("/kereso")
    @RequiresPermission("irat.report")
    public IratKereso.Result kereso(@RequestParam(required = false) String iktatoszam,
                                    @RequestParam(required = false) Kuldemeny.Allapot allapot,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate erkezesTol,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate erkezesIg,
                                    @RequestParam(required = false) String partner,
                                    @RequestParam(required = false) UUID partnerId,
                                    @RequestParam(required = false) String ugyintezo,
                                    @RequestParam(required = false) UUID ugyintezoId,
                                    @RequestParam(required = false) String targy,
                                    @RequestParam(required = false) Integer mellekletekSzama,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return kereso.search(new IratKereso.Filter(iktatoszam, allapot, erkezesTol, erkezesIg, partner, partnerId,
                ugyintezo, ugyintezoId, targy, mellekletekSzama), page, size);
    }

    /** Az egylépéses művelethez mindhárom lépés joga kell. */
    private void requireAll() {
        permissions.require("irat.erkeztetes");
        permissions.require("irat.bontas");
        permissions.require("irat.iktatas");
    }
}
