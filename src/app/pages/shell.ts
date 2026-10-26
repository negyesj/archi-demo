import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../core/auth';
import { AdminApi } from '../core/admin-api';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule],
  template: `
    <mat-toolbar class="topbar">
      <mat-icon>admin_panel_settings</mat-icon>&nbsp;Tenant admin
      <span class="spacer"></span>
      <span class="muted">{{ api.me()?.fullName }} ({{ api.me()?.role === 'PLATFORM_ADMIN' ? 'platform admin' : 'support' }})</span>
      <button mat-icon-button (click)="auth.logout()"><mat-icon>logout</mat-icon></button>
    </mat-toolbar>
    <mat-sidenav-container class="shell">
      <mat-sidenav mode="side" opened class="sidenav">
        <mat-nav-list>
          <a mat-list-item routerLink="/tenants" routerLinkActive="active"><mat-icon matListItemIcon>domain</mat-icon><span matListItemTitle>Tenantok</span></a>
          <a mat-list-item routerLink="/catalog" routerLinkActive="active"><mat-icon matListItemIcon>widgets</mat-icon><span matListItemTitle>Szolgáltatás-katalógus</span></a>
          <a mat-list-item routerLink="/admin-users" routerLinkActive="active"><mat-icon matListItemIcon>manage_accounts</mat-icon><span matListItemTitle>Admin felhasználók</span></a>
          <a mat-list-item routerLink="/audit" routerLinkActive="active"><mat-icon matListItemIcon>history</mat-icon><span matListItemTitle>Admin audit</span></a>
          <a mat-list-item routerLink="/loglevels" routerLinkActive="active"><mat-icon matListItemIcon>tune</mat-icon><span matListItemTitle>Naplózási szintek</span></a>
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content class="content"><router-outlet /></mat-sidenav-content>
    </mat-sidenav-container>
  `,
})
export class Shell {
  readonly auth = inject(AuthService);
  readonly api = inject(AdminApi);
}
