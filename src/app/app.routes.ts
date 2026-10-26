import { Routes } from '@angular/router';
import { permissionGuard, tenantGuard } from './core/guards';
import { TenantSelect } from './layout/tenant-select';
import { Shell } from './layout/shell';
import { Home } from './layout/home';

/**
 * URL szerkezet:
 *   /                     tenant választó
 *   /t/{tenant}           a tenant login képernyője (Keycloak), bejelentkezve a kezdőlap
 *   /t/{tenant}/{szolgáltatás}/...   a szolgáltatások felületei (lusta betöltéssel)
 */
export const routes: Routes = [
  { path: '', component: TenantSelect },
  {
    path: 't/:tenant',
    component: Shell,
    canActivate: [tenantGuard],
    children: [
      { path: '', component: Home },
      { path: 'iam/users', canActivate: [permissionGuard], data: { service: 'iam', permissions: ['iam.read'] },
        loadComponent: () => import('./features/iam/users').then((m) => m.Users) },
      { path: 'iam/groups', canActivate: [permissionGuard], data: { service: 'iam', permissions: ['iam.read'] },
        loadComponent: () => import('./features/iam/groups').then((m) => m.Groups) },
      { path: 'iam/org-units', canActivate: [permissionGuard], data: { service: 'iam', permissions: ['iam.read'] },
        loadComponent: () => import('./features/iam/org-units').then((m) => m.OrgUnits) },
      { path: 'iam/permissions', canActivate: [permissionGuard], data: { service: 'iam', permissions: ['iam.permission.manage'] },
        loadComponent: () => import('./features/iam/permissions').then((m) => m.Permissions) },
      { path: 'iam/integrations', canActivate: [permissionGuard], data: { service: 'iam', permissions: ['integration.manage'] },
        loadComponent: () => import('./features/iam/integrations').then((m) => m.Integrations) },
      { path: 'audit', canActivate: [permissionGuard], data: { service: 'audit', permissions: ['audit.read'] },
        loadComponent: () => import('./features/audit/audit').then((m) => m.Audit) },
      { path: 'partner', canActivate: [permissionGuard], data: { service: 'partner', permissions: ['partner.read'] },
        loadComponent: () => import('./features/partner/partners').then((m) => m.Partners) },
      { path: 'irat/erkeztetes', canActivate: [permissionGuard], data: { service: 'irat', permissions: ['irat.erkeztetes'] },
        loadComponent: () => import('./features/irat/erkeztetes').then((m) => m.Erkeztetes) },
      { path: 'irat/feldolgozas', canActivate: [permissionGuard], data: { service: 'irat', permissions: ['irat.read', 'irat.bontas', 'irat.iktatas'] },
        loadComponent: () => import('./features/irat/feldolgozas').then((m) => m.Feldolgozas) },
      { path: 'irat/kereso', canActivate: [permissionGuard], data: { service: 'irat', permissions: ['irat.report'] },
        loadComponent: () => import('./features/irat/kereso').then((m) => m.Kereso) },
      { path: '**', redirectTo: '' },
    ],
  },
  { path: '**', redirectTo: '' },
];
