import { Routes } from '@angular/router';
import { adminGuard } from './core/guard';
import { Shell } from './pages/shell';

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    canActivate: [adminGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tenants' },
      { path: 'tenants', loadComponent: () => import('./pages/tenants').then((m) => m.Tenants) },
      { path: 'tenants/:code', loadComponent: () => import('./pages/tenant-detail').then((m) => m.TenantDetail) },
      { path: 'catalog', loadComponent: () => import('./pages/catalog').then((m) => m.Catalog) },
      { path: 'admin-users', loadComponent: () => import('./pages/admin-users').then((m) => m.AdminUsers) },
      { path: 'audit', loadComponent: () => import('./pages/audit').then((m) => m.AdminAudit) },
      { path: 'loglevels', loadComponent: () => import('./pages/loglevels').then((m) => m.LogLevels) },
    ],
  },
  { path: '**', redirectTo: '' },
];
