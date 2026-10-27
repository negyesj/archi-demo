import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth';
import { AdminApi } from '../core/admin-api';
import { ConfigService } from '../core/config';
import { DmsLogo } from '../shared/dms-logo';

interface NavItem { path: string; label: string; icon: string; }
interface NavGroup { label: string; icon: string; items: NavItem[]; }

/** A tenant admin felület menüje: bal szélső főmenü-oszlop (csoportok) és almenü (menüpontok). */
const NAV: NavGroup[] = [
  {
    label: 'Tenantok',
    icon: 'domain',
    items: [
      { path: '/tenants', label: 'Tenantok', icon: 'domain' },
      { path: '/catalog', label: 'Szolgáltatás-katalógus', icon: 'widgets' },
    ],
  },
  {
    label: 'Rendszer',
    icon: 'dns',
    items: [
      { path: '/admin-users', label: 'Admin felhasználók', icon: 'manage_accounts' },
      { path: '/audit', label: 'Admin audit', icon: 'history' },
      { path: '/loglevels', label: 'Naplózási szintek', icon: 'tune' },
    ],
  },
];

/** DMS One (K4) elrendezés: lebegő felső kártya, főmenü-oszlop, 230px-es almenü, munkaterület. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatMenuModule, MatTooltipModule, DmsLogo],
  template: `
    <div class="k4main">
      <header class="k4main-top">
        <div class="k4main-bartop-left">
          <a class="dms-logo" routerLink="/tenants" matTooltip="Tenantok"><app-dms-logo /></a>
        </div>
        <div class="k4main-bartop-center">
          <span class="sysname"><b>{{ config.value.appName }}</b></span>
        </div>
        <div class="k4main-bartop-right">
          <span class="appverzio">{{ api.me()?.role === 'PLATFORM_ADMIN' ? 'platform admin' : 'support' }}</span>
          <span class="username">{{ api.me()?.fullName }}</span>
          <button class="avatar-btn" [matMenuTriggerFor]="userMenu" [matTooltip]="api.me()?.fullName ?? ''">{{ initials() }}</button>
          <mat-menu #userMenu="matMenu" xPosition="before">
            <button mat-menu-item (click)="auth.logout()"><mat-icon>logout</mat-icon>Kijelentkezés</button>
          </mat-menu>
        </div>
      </header>
      <div class="k4main-bottom">
        <nav class="k4main-fav-column">
          @for (g of nav; track g.label) {
            <a class="fav-item" [class.selected]="active().label === g.label" [routerLink]="g.items[0].path">
              <mat-icon>{{ g.icon }}</mat-icon><span class="fav-label">{{ g.label }}</span>
            </a>
          }
        </nav>
        <aside class="k4main-left">
          <div class="k4main-menu">
            <div class="area-navbar-group">
              <div class="group-title">{{ active().label }}</div>
              @for (i of active().items; track i.path) {
                <a class="lbitem" [routerLink]="i.path" routerLinkActive="selected">
                  <mat-icon>{{ i.icon }}</mat-icon><span class="lbitem-text">{{ i.label }}</span>
                </a>
              }
            </div>
          </div>
        </aside>
        <main class="k4main-right"><router-outlet /></main>
      </div>
    </div>
  `,
})
export class Shell {
  readonly auth = inject(AuthService);
  readonly api = inject(AdminApi);
  readonly config = inject(ConfigService);
  private readonly router = inject(Router);
  readonly nav = NAV;

  private readonly url = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map(() => this.router.url)),
    { initialValue: this.router.url },
  );

  /** Az aktuális oldalt tartalmazó menücsoport (a tenant részletei a Tenantok csoportba tartoznak). */
  readonly active = computed(() => {
    const path = this.url().split('?')[0];
    return NAV.find((g) => g.items.some((i) => path === i.path || path.startsWith(i.path + '/'))) ?? NAV[0];
  });

  readonly initials = computed(() => {
    const parts = (this.api.me()?.fullName ?? '').trim().split(/\s+/).filter(Boolean);
    return parts.length === 0 ? '?' : (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  });
}
