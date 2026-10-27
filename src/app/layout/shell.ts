import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TenantService } from '../core/tenant';
import { AuthService } from '../core/auth';
import { UserContextService } from '../core/context';
import { ConfigService } from '../core/config';
import { DmsLogo } from '../shared/dms-logo';
import { MENU, MenuGroup } from './menu';

/**
 * DMS One (K4) elrendezés: lebegő felső kártya, bal szélső főmenü-oszlop (a menücsoportok), 230px-es almenü
 * a kiválasztott csoport menüpontjaival, jobbra a munkaterület.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatMenuModule, MatTooltipModule, DmsLogo],
  template: `
    <div class="k4main">
      <header class="k4main-top">
        <div class="k4main-bartop-left">
          <a class="dms-logo" [routerLink]="base()" matTooltip="Kezdőlap"><app-dms-logo /></a>
        </div>
        <div class="k4main-bartop-center">
          <span class="sysname">{{ config.value.appName }} · <b>{{ tenant()?.name }}</b></span>
        </div>
        <div class="k4main-bartop-right">
          <span class="appverzio">{{ tenant()?.code }}</span>
          <span class="username">{{ auth.userName() }}</span>
          <button class="avatar-btn" [matMenuTriggerFor]="userMenu" [matTooltip]="auth.userName()">{{ initials() }}</button>
          <mat-menu #userMenu="matMenu" xPosition="before">
            <a mat-menu-item routerLink="/"><mat-icon>swap_horiz</mat-icon>Másik szervezet</a>
            <button mat-menu-item (click)="auth.logout()"><mat-icon>logout</mat-icon>Kijelentkezés</button>
          </mat-menu>
        </div>
      </header>
      <div class="k4main-bottom">
        <nav class="k4main-fav-column">
          <a class="fav-item" [class.selected]="!active()" [routerLink]="base()">
            <mat-icon>home</mat-icon><span class="fav-label">Kezdőlap</span>
          </a>
          @for (g of menu(); track g.label) {
            <a class="fav-item" [class.selected]="active()?.label === g.label" [routerLink]="base() + '/' + g.items[0].path">
              <mat-icon>{{ g.icon }}</mat-icon><span class="fav-label">{{ g.label }}</span>
            </a>
          }
        </nav>
        <aside class="k4main-left">
          <div class="k4main-menu">
            @for (g of leftGroups(); track g.label) {
              <div class="area-navbar-group">
                <div class="group-title">{{ g.label }}</div>
                @for (i of g.items; track i.path) {
                  <a class="lbitem" [routerLink]="base() + '/' + i.path" routerLinkActive="selected">
                    <mat-icon>{{ i.icon }}</mat-icon><span class="lbitem-text">{{ i.label }}</span>
                  </a>
                }
              </div>
            }
          </div>
        </aside>
        <main class="k4main-right"><router-outlet /></main>
      </div>
    </div>
  `,
})
export class Shell {
  readonly auth = inject(AuthService);
  readonly config = inject(ConfigService);
  private readonly router = inject(Router);
  private readonly tenants = inject(TenantService);
  private readonly context = inject(UserContextService);
  readonly tenant = this.tenants.current;

  private readonly url = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map(() => this.router.url)),
    { initialValue: this.router.url },
  );

  readonly base = computed(() => `/t/${this.tenant()?.code ?? ''}`);

  /** A felhasználó számára látható menücsoportok (engedélyezett szolgáltatás és jogosultság szerint). */
  readonly menu = computed<MenuGroup[]>(() => {
    const perms = this.context.permissions();
    const services = this.tenant()?.services ?? [];
    return MENU.map((g) => ({
      ...g,
      items: g.items.filter((i) => services.includes(i.service) && i.permissions.some((p) => perms.has(p))),
    })).filter((g) => g.items.length > 0);
  });

  /** Az aktuális oldalhoz tartozó főmenü-csoport (kezdőlapon nincs). */
  readonly active = computed(() => {
    const path = this.url().split('?')[0].substring(this.base().length + 1);
    return this.menu().find((g) => g.items.some((i) => path === i.path || path.startsWith(i.path + '/'))) ?? null;
  });

  /** Az almenüben a kiválasztott csoport, kezdőlapon minden csoport látszik. */
  readonly leftGroups = computed(() => {
    const a = this.active();
    return a ? [a] : this.menu();
  });

  readonly initials = computed(() => {
    const parts = this.auth.userName().trim().split(/\s+/).filter(Boolean);
    return parts.length === 0 ? '?' : (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  });
}
