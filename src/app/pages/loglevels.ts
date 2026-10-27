import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AdminApi } from '../core/admin-api';
import { NotifyService } from '../core/notify';

interface Setting { key: string; service: string; logger: string; level: string; tenant: string | null; expiresAt: string | null; createdBy: string; }

/**
 * Központi, futás közben állítható naplózási szint: szolgáltatásonként, logger (csomag) szerint, opcionálisan egy
 * tenantra szűkítve és lejárati idővel. Minden futó példányra érvényes.
 */
@Component({
  selector: 'app-loglevels',
  imports: [DatePipe, FormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule],
  template: `
    <h1>Naplózási szintek</h1>
    <div class="filters">
      <div class="form-item"><label class="form-label">Szolgáltatás</label><mat-form-field><mat-select [(ngModel)]="f.service">
          @for (s of services; track s) { <mat-option [value]="s">{{ s }}</mat-option> }
        </mat-select></mat-form-field></div>
      <div class="form-item wide"><label class="form-label">Logger (csomag, üres = ROOT)</label><mat-form-field><input matInput [(ngModel)]="f.logger" placeholder="hu.dmsone.archi" /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Szint</label><mat-form-field><mat-select [(ngModel)]="f.level">@for (l of levels; track l) { <mat-option [value]="l">{{ l }}</mat-option> }</mat-select></mat-form-field></div>
      <div class="form-item"><label class="form-label">Csak ez a tenant</label><mat-form-field><input matInput [(ngModel)]="f.tenant" /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Lejárat (perc)</label><mat-form-field><input matInput type="number" min="1" max="1440" [(ngModel)]="f.minutes" /></mat-form-field></div>
      <button mat-flat-button [disabled]="!f.service || !f.level" (click)="set()"><mat-icon>tune</mat-icon> Beállítás</button>
    </div>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="service"><th mat-header-cell *matHeaderCellDef>Szolgáltatás</th><td mat-cell *matCellDef="let s">{{ s.service }}</td></ng-container>
      <ng-container matColumnDef="logger"><th mat-header-cell *matHeaderCellDef>Logger</th><td mat-cell *matCellDef="let s">{{ s.logger }}</td></ng-container>
      <ng-container matColumnDef="level"><th mat-header-cell *matHeaderCellDef>Szint</th><td mat-cell *matCellDef="let s">{{ s.level }}</td></ng-container>
      <ng-container matColumnDef="tenant"><th mat-header-cell *matHeaderCellDef>Tenant</th><td mat-cell *matCellDef="let s">{{ s.tenant ?? 'mind' }}</td></ng-container>
      <ng-container matColumnDef="expires"><th mat-header-cell *matHeaderCellDef>Lejár</th><td mat-cell *matCellDef="let s">{{ s.expiresAt ? (s.expiresAt | date: 'yyyy.MM.dd HH:mm') : 'nem jár le' }}</td></ng-container>
      <ng-container matColumnDef="by"><th mat-header-cell *matHeaderCellDef>Beállította</th><td mat-cell *matCellDef="let s">{{ s.createdBy }}</td></ng-container>
      <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let s" class="actions"><button mat-icon-button (click)="reset(s)"><mat-icon>restart_alt</mat-icon></button></td></ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
  `,
})
export class LogLevels {
  private readonly api = inject(AdminApi);
  private readonly notify = inject(NotifyService);
  readonly services = ['*', 'gateway', 'tenant-admin', 'provisioner', 'iam', 'audit', 'partner', 'irat', 'webhook', 'outbox-relay', 'reporting-projector'];
  readonly levels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];
  readonly cols = ['service', 'logger', 'level', 'tenant', 'expires', 'by', 'actions'];
  readonly rows = signal<Setting[]>([]);
  f = { service: 'irat', logger: '', level: 'DEBUG', tenant: '', minutes: 60 as number | null };

  constructor() {
    this.load();
  }

  load(): void {
    this.api.get<Setting[]>('loglevels').subscribe((s) => this.rows.set(s));
  }

  set(): void {
    this.api.put<Setting>('loglevels', { ...this.f, logger: this.f.logger || null, tenant: this.f.tenant || null })
      .subscribe(() => { this.notify.ok('Naplózási szint beállítva'); this.load(); });
  }

  reset(s: Setting): void {
    this.api.delete('loglevels', { key: s.key }).subscribe(() => { this.notify.ok('Visszaállítva'); this.load(); });
  }
}
