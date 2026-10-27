import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { AdminApi } from '../core/admin-api';
import { NotifyService } from '../core/notify';
import { CatalogEntry, STATUS_NEV, Tenant, TenantStatus } from './models';

@Component({
  selector: 'app-tenant-create',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatCheckboxModule],
  template: `
    <h2 mat-dialog-title>Új tenant</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-col">
        <div class="form-item"><label class="form-label">Kód (URL-ben: /t/kód)</label><mat-form-field><input matInput formControlName="code" /><mat-hint>kisbetű és szám, 2–30 karakter</mat-hint></mat-form-field></div>
        <div class="form-item"><label class="form-label">Név</label><mat-form-field><input matInput formControlName="name" /></mat-form-field></div>
        <h4>Szolgáltatások</h4>
        @for (s of catalog(); track s.id) {
          @if (!s.core) {
            <mat-checkbox [checked]="selected().has(s.id)" (change)="toggle(s.id, $event.checked)">
              {{ s.name }} <small class="muted">({{ s.id }}{{ s.manifest.dependsOn?.length ? ', függ: ' + s.manifest.dependsOn!.join(', ') : '' }})</small>
            </mat-checkbox>
          }
        }
        <h4>Első ügyfél-adminisztrátor</h4>
        <div class="form-item"><label class="form-label">Felhasználónév</label><mat-form-field><input matInput formControlName="adminUsername" /></mat-form-field></div>
        <div class="form-item"><label class="form-label">Név</label><mat-form-field><input matInput formControlName="adminName" /></mat-form-field></div>
        <div class="form-item"><label class="form-label">E-mail</label><mat-form-field><input matInput formControlName="adminEmail" /></mat-form-field></div>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Mégse</button>
      <button mat-flat-button [disabled]="form.invalid" (click)="save()">Létrehozás</button>
    </mat-dialog-actions>
  `,
})
export class TenantCreateDialog {
  private readonly api = inject(AdminApi);
  private readonly ref = inject(MatDialogRef<TenantCreateDialog>);
  readonly catalog = signal<CatalogEntry[]>([]);
  readonly selected = signal(new Set<string>());
  readonly form = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9]{1,29}$/)]],
    name: ['', Validators.required],
    adminUsername: ['admin', Validators.required],
    adminName: ['', Validators.required],
    adminEmail: ['', [Validators.required, Validators.email]],
  });

  constructor() {
    this.api.get<CatalogEntry[]>('catalog').subscribe((c) => this.catalog.set(c));
  }

  toggle(id: string, on: boolean): void {
    const s = new Set(this.selected());
    if (on) { s.add(id); } else { s.delete(id); }
    this.selected.set(s);
  }

  save(): void {
    this.api.post<Tenant>('tenants', { ...this.form.getRawValue(), enabledServices: [...this.selected()], settings: {} })
      .subscribe((t) => this.ref.close(t));
  }
}

@Component({
  selector: 'app-tenants',
  imports: [DatePipe, RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatDialogModule],
  template: `
    <div class="page-header">
      <h1>Tenantok</h1>
      <span>
        <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Frissítés</button>
        @if (api.isAdmin()) { <button mat-flat-button (click)="create()"><mat-icon>add</mat-icon> Új tenant</button> }
      </span>
    </div>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Kód</th><td mat-cell *matCellDef="let t"><a [routerLink]="['/tenants', t.code]">{{ t.code }}</a></td></ng-container>
      <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Név</th><td mat-cell *matCellDef="let t">{{ t.name }}</td></ng-container>
      <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Állapot</th><td mat-cell *matCellDef="let t">{{ statusNev(t.status) }}</td></ng-container>
      <ng-container matColumnDef="services"><th mat-header-cell *matHeaderCellDef>Szolgáltatások</th><td mat-cell *matCellDef="let t">{{ t.enabledServices.join(', ') }}</td></ng-container>
      <ng-container matColumnDef="updated"><th mat-header-cell *matHeaderCellDef>Módosítva</th><td mat-cell *matCellDef="let t">{{ t.updatedAt | date: 'yyyy.MM.dd HH:mm' }}</td></ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
  `,
})
export class Tenants {
  readonly api = inject(AdminApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  readonly cols = ['code', 'name', 'status', 'services', 'updated'];
  readonly rows = signal<Tenant[]>([]);

  constructor() {
    this.load();
  }

  statusNev(s: TenantStatus): string {
    return STATUS_NEV[s] ?? s;
  }

  load(): void {
    this.api.get<Tenant[]>('tenants').subscribe((t) => this.rows.set(t));
  }

  create(): void {
    this.dialog.open(TenantCreateDialog, { width: '560px' }).afterClosed().subscribe((t?: Tenant) => {
      if (t) {
        this.notify.ok(`Tenant létrehozása elindult: ${t.code}`);
        this.load();
      }
    });
  }
}
