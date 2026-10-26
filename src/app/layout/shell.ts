import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { TenantService } from '../core/tenant';
import { AuthService } from '../core/auth';
import { UserContextService } from '../core/context';
import { MENU } from './menu';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule,
    MatButtonModule, MatMenuModule],
  template: `
    <mat-toolbar class="topbar">
      <a mat-button [routerLink]="['/t', tenant()?.code]"><mat-icon>apps</mat-icon> {{ tenant()?.name }}</a>
      <span class="spacer"></span>
      <button mat-button [matMenuTriggerFor]="userMenu"><mat-icon>account_circle</mat-icon> {{ auth.userName() }}</button>
      <mat-menu #userMenu="matMenu">
        <a mat-menu-item routerLink="/"><mat-icon>swap_horiz</mat-icon>Másik szervezet</a>
        <button mat-menu-item (click)="auth.logout()"><mat-icon>logout</mat-icon>Kijelentkezés</button>
      </mat-menu>
    </mat-toolbar>
    <mat-sidenav-container class="shell">
      <mat-sidenav mode="side" opened class="sidenav">
        @for (g of menu(); track g.label) {
          <h3 class="menu-group">{{ g.label }}</h3>
          <mat-nav-list>
            @for (i of g.items; track i.path) {
              <a mat-list-item [routerLink]="'/t/' + tenant()?.code + '/' + i.path" routerLinkActive="active">
                <mat-icon matListItemIcon>{{ i.icon }}</mat-icon>
                <span matListItemTitle>{{ i.label }}</span>
              </a>
            }
          </mat-nav-list>
        }
      </mat-sidenav>
      <mat-sidenav-content class="content">
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
})
export class Shell {
  readonly auth = inject(AuthService);
  private readonly tenants = inject(TenantService);
  private readonly context = inject(UserContextService);
  readonly tenant = this.tenants.current;

  readonly menu = computed(() => {
    const perms = this.context.permissions();
    const t = this.tenant();
    return MENU.map((g) => ({
      label: g.label,
      items: g.items.filter((i) => (t?.services ?? []).includes(i.service) && i.permissions.some((p) => perms.has(p))),
    })).filter((g) => g.items.length > 0);
  });
}
