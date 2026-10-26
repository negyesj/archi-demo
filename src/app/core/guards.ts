import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth';
import { TenantService } from './tenant';
import { UserContextService } from './context';

/**
 * /t/:tenant – a tenant létezik és aktív; ha nincs bejelentkezve, a tenant login oldalára irányít (Keycloak);
 * utána betölti az effektív jogosultságokat.
 */
export const tenantGuard: CanActivateFn = async (route: ActivatedRouteSnapshot) => {
  const router = inject(Router);
  const tenants = inject(TenantService);
  const auth = inject(AuthService);
  const context = inject(UserContextService);
  const code = route.paramMap.get('tenant') ?? '';
  const info = await tenants.activate(code);
  if (!info) {
    return router.createUrlTree(['/'], { queryParams: { ismeretlen: code } });
  }
  if (!(await auth.ensureLoggedIn(code))) {
    return false;
  }
  if (!context.context()) {
    await context.load(code);
  }
  return true;
};

/** Menüpont jogosultság: a route data.permissions bármelyike elég. */
export const permissionGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const perms = (route.data['permissions'] as string[] | undefined) ?? [];
  const service = route.data['service'] as string | undefined;
  const context = inject(UserContextService);
  const tenants = inject(TenantService);
  const router = inject(Router);
  if (service && !tenants.hasService(service)) {
    return router.createUrlTree(['/t', tenants.code()]);
  }
  return perms.length === 0 || context.has(...perms) ? true : router.createUrlTree(['/t', tenants.code()]);
};
