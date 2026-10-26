package hu.dmsone.archi.iam.permission;

import hu.dmsone.archi.platform.security.EffectivePermissions;
import hu.dmsone.archi.platform.web.permission.PermissionService;
import hu.dmsone.archi.platform.web.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/t/{tenant}/iam")
public class PermissionController {

    private final GrantService grants;
    private final PermissionService permissionService;

    public PermissionController(GrantService grants, PermissionService permissionService) {
        this.grants = grants;
        this.permissionService = permissionService;
    }

    /** A bejelentkezett felhasználó effektív jogosultságai (a többi szolgáltatás és a felület is ezt használja). */
    @GetMapping("/me/context")
    public EffectivePermissions me() {
        return permissionService.current();
    }

    @GetMapping("/permissions")
    @RequiresPermission({"iam.permission.manage", "iam.read"})
    public List<PermissionRepository.Permission> permissions() {
        return grants.permissions();
    }

    @GetMapping("/grants")
    @RequiresPermission({"iam.permission.manage", "iam.read"})
    public List<PermissionRepository.Grant> list(@RequestParam(required = false) String subjectType,
                                                 @RequestParam(required = false) UUID subjectId,
                                                 @RequestParam(required = false) String permissionCode) {
        return grants.grants(subjectType, subjectId, permissionCode);
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission("iam.permission.manage")
    public PermissionRepository.Grant grant(@Valid @RequestBody GrantService.GrantRequest req) {
        return grants.grant(req);
    }

    @DeleteMapping("/grants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission("iam.permission.manage")
    public void revoke(@PathVariable UUID id) {
        grants.revoke(id);
    }
}
