import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { TenantInfo, TenantService } from '../core/tenant';
import { ConfigService } from '../core/config';

/**
 * Kezdőlap: tenant választás. Nem listázza az ügyfeleket (az ügyfélnév-lista érzékeny adat): a felhasználó a tenant
 * kódot adja meg, vagy a böngészőben megjegyzett, korábban használt tenantok közül választ.
 */
@Component({
  selector: 'app-tenant-select',
  imports: [FormsModule, RouterLink, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatListModule, MatIconModule],
  template: `
    <div class="center-page">
      <mat-card class="narrow">
        <mat-card-header>
          <mat-card-title>{{ config.value.appName }}</mat-card-title>
          <mat-card-subtitle>Adja meg a szervezete azonosítóját</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form (ngSubmit)="go()">
            <mat-form-field class="full">
              <mat-label>Tenant azonosító</mat-label>
              <input matInput name="code" [(ngModel)]="code" autocomplete="organization" required />
              @if (error()) { <mat-hint class="error-text">{{ error() }}</mat-hint> }
            </mat-form-field>
            <button mat-flat-button class="full" type="submit" [disabled]="busy()">Tovább a bejelentkezéshez</button>
          </form>
          @if (recent.length) {
            <h4>Korábban használt</h4>
            <mat-nav-list>
              @for (t of recent; track t.code) {
                <a mat-list-item [routerLink]="['/t', t.code]">
                  <mat-icon matListItemIcon>domain</mat-icon>
                  <span matListItemTitle>{{ t.name }}</span>
                  <span matListItemLine>{{ t.code }}</span>
                </a>
              }
            </mat-nav-list>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
})
export class TenantSelect {
  private readonly tenants = inject(TenantService);
  private readonly router = inject(Router);
  readonly config = inject(ConfigService);
  readonly recent: TenantInfo[] = this.tenants.recent();
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  code = inject(ActivatedRoute).snapshot.queryParamMap.get('ismeretlen') ?? '';

  constructor() {
    if (this.code) {
      this.error.set('Ismeretlen vagy nem elérhető azonosító.');
    }
  }

  async go(): Promise<void> {
    const code = this.code.trim().toLowerCase();
    this.busy.set(true);
    const info = await this.tenants.lookup(code);
    this.busy.set(false);
    if (!info) {
      this.error.set('Ismeretlen vagy nem elérhető azonosító.');
      return;
    }
    await this.router.navigate(['/t', code]);
  }
}
