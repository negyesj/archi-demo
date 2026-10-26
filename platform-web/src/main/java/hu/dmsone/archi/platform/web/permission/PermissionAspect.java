package hu.dmsone.archi.platform.web.permission;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

/** A {@link RequiresPermission} annotáció kiértékelése. */
@Aspect
public class PermissionAspect {

    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Before("@annotation(requiresPermission)")
    public void check(RequiresPermission requiresPermission) {
        permissionService.require(requiresPermission.value());
    }
}
