import { Component, computed, inject, input, signal, effect } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialog } from '@angular/material/dialog';
import { AdminApi } from '../core/admin-api';
import { NotifyService } from '../core/notify';
import { confirm, SecretDialog } from '../shared/confirm';
import { CatalogEntry, STATUS_NEV, Tenant } from './models';

/** Tenant részletek: konfiguráció (szolgáltatások, beállítások), állapotváltás, első admin jelszava. */
@Component({
  selector: 'app-tenant-detail',
  imports: [DatePipe, FormsModule, RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatCheckboxModule, MatFormFieldModule, MatInputModule],
  template: `
    <a mat-button routerLink="/tenants"><mat-icon>arrow_back</mat-icon> Tenantok</a>
    @if (tenant(); as t) {
      <h1>{{ t.name }} <small class="muted">({{ t.code }})</small></h1>
      <mat-card class="form-card">
        <mat-card-content>
          <p>Állapot: <strong>{{ statusNev() }}</strong> · létrehozva: {{ t.createdAt | date: 'yyyy.MM.dd HH:mm' }}</p>
          @if (t.lastError) { <p class="error-text">Hiba: {{ t.lastError }}</p> }
          <p>Első admin: {{ t.adminName }} ({{ t.adminUsername }}, {{ t.adminEmail }})</p>
          <p>Bejelentkezés: <code>/t/{{ t.code }}</code></p>
        </mat-card-content>
        @if (api.isAdmin()) {
          <mat-card-actions>
            @if (t.status === 'ACTIVE') { <button mat-stroked-button (click)="status('suspend')">Felfüggesztés</button> }
            @if (t.status === 'SUSPENDED') { <button mat-stroked-button (click)="status('activate')">Aktiválás</button> }
            @if (t.status === 'SUSPENDED' || t.status === 'FAILED') { <button mat-stroked-button (click)="status('archive')">Archiválás</button> }
            @if (t.status === 'FAILED' || t.status === 'PROVISIONING') { <button mat-stroked-button (click)="status('provision')">Provisioning újraindítása</button> }
            @if (t.status === 'ACTIVE') { <button mat-stroked-button (click)="bootstrapPassword()">Első admin jelszava</button> }
          </mat-card-actions>
        }
      </mat-card>

      <mat-card class="form-card">
        <mat-card-header><mat-card-title>Engedélyezett szolgáltatások</mat-card-title></mat-card-header>
        <mat-card-content class="form-col">
          @for (s of catalog(); track s.id) {
            <mat-checkbox [disabled]="s.core || !api.isAdmin()" [checked]="s.core || selected().has(s.id)" (change)="toggle(s.id, $event.checked)">
              {{ s.name }} <small class="muted">({{ s.id }} {{ s.version }}{{ s.core ? ', alapszolgáltatás' : '' }}{{ s.manifest.dependsOn?.length ? ', függ: ' + s.manifest.dependsOn!.join(', ') : '' }})</small>
            </mat-checkbox>
          }
        </mat-card-content>
        @if (api.isAdmin()) {
          <mat-card-actions><button mat-flat-button [disabled]="!dirty()" (click)="saveServices()">Mentés</button></mat-card-actions>
        }
      </mat-card>

      <mat-card class="form-card">
        <mat-card-header><mat-card-title>Beállítások</mat-card-title></mat-card-header>
        <mat-card-content class="form-col">
          <mat-form-field><mat-label>Név</mat-label><input matInput [(ngModel)]="name" [disabled]="!api.isAdmin()" /></mat-form-field>
          <mat-form-field><mat-label>Iktatókönyv (iktatoszam.konyv)</mat-label><input matInput [(ngModel)]="konyv" [disabled]="!api.isAdmin()" /></mat-form-field>
          <mat-form-field><mat-label>Iktatószám minta (iktatoszam.minta)</mat-label><input matInput [(ngModel)]="minta" [disabled]="!api.isAdmin()" /><mat-hint>{{ '{konyv}' }}, {{ '{ev}' }}, {{ '{sorszam}' }}</mat-hint></mat-form-field>
        </mat-card-content>
        @if (api.isAdmin()) {
          <mat-card-actions><button mat-flat-button (click)="saveSettings()">Mentés</button></mat-card-actions>
        }
      </mat-card>
    }
  `,
})
export class TenantDetail {
  readonly code = input.required<string>();
  readonly api = inject(AdminApi);
  private readonly notify = inject(NotifyService);
  private readonly dialog = inject(MatDialog);
  readonly tenant = signal<Tenant | null>(null);
  readonly catalog = signal<CatalogEntry[]>([]);
  readonly selected = signal(new Set<string>());
  readonly statusNev = computed(() => { const t = this.tenant(); return t ? (STATUS_NEV[t.status] ?? t.status) : ''; });
  readonly dirty = computed(() => {
    const t = this.tenant();
    if (!t) { return false; }
    const now = [...this.selected()].sort().join(',');
    const before = t.enabledServices.filter((s) => !this.catalog().find((c) => c.id === s)?.core).sort().join(',');
    return now !== before;
  });
  name = '';
  konyv = '';
  minta = '';

  constructor() {
    this.api.get<CatalogEntry[]>('catalog').subscribe((c) => this.catalog.set(c));
    effect(() => this.load(this.code()));
  }

  load(code: string): void {
    this.api.get<Tenant>(`tenants/${code}`).subscribe((t) => this.apply(t));
  }

  private apply(t: Tenant): void {
    this.tenant.set(t);
    this.selected.set(new Set(t.enabledServices));
    this.name = t.name;
    this.konyv = t.settings['iktatoszam.konyv'] ?? '';
    this.minta = t.settings['iktatoszam.minta'] ?? '';
  }

  toggle(id: string, on: boolean): void {
    const s = new Set(this.selected());
    if (on) { s.add(id); } else { s.delete(id); }
    this.selected.set(s);
  }

  saveServices(): void {
    const services = [...this.selected()].filter((s) => !this.catalog().find((c) => c.id === s)?.core);
    this.api.put<Tenant>(`tenants/${this.code()}/services`, { services }).subscribe((t) => { this.apply(t); this.notify.ok('Szolgáltatások mentve'); });
  }

  saveSettings(): void {
    const t = this.tenant()!;
    const settings: Record<string, string> = { ...t.settings };
    if (this.konyv) { settings['iktatoszam.konyv'] = this.konyv; } else { delete settings['iktatoszam.konyv']; }
    if (this.minta) { settings['iktatoszam.minta'] = this.minta; } else { delete settings['iktatoszam.minta']; }
    this.api.put<Tenant>(`tenants/${this.code()}`, { name: this.name, settings }).subscribe((r) => { this.apply(r); this.notify.ok('Beállítások mentve'); });
  }

  status(action: 'suspend' | 'activate' | 'archive' | 'provision'): void {
    const labels = { suspend: 'Felfüggesztés', activate: 'Aktiválás', archive: 'Archiválás', provision: 'Provisioning újraindítása' };
    confirm(this.dialog, { title: labels[action], message: `${this.tenant()!.name} – biztosan?`, ok: labels[action] })
      .subscribe((ok) => ok && this.api.post<Tenant>(`tenants/${this.code()}/${action}`).subscribe((t) => this.apply(t)));
  }

  bootstrapPassword(): void {
    this.api.post<{ username: string; password: string }>(`tenants/${this.code()}/bootstrap-password`).subscribe((r) =>
      this.dialog.open(SecretDialog, { data: { title: 'Első ügyfél-admin ideiglenes jelszava', message: `Felhasználónév: ${r.username}`, secret: r.password } }));
  }
}
