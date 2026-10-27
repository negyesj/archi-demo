import { Component, inject, signal } from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { AdminApi } from '../core/admin-api';
import { isoDate } from '../core/util';

interface Row { id: number; occurredAt: string; actorId: string; actorName: string; action: string; targetType: string; targetId: string; details: unknown; }

/** Tenant admin audit napló: az elvégzett admin műveletek visszakeresése. */
@Component({
  selector: 'app-admin-audit',
  imports: [DatePipe, JsonPipe, FormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatDatepickerModule],
  template: `
    <h1>Admin audit napló</h1>
    <div class="filters">
      <div class="form-item"><label class="form-label">Ettől</label><mat-form-field><input matInput [matDatepicker]="d1" [(ngModel)]="from" /><mat-datepicker-toggle matIconSuffix [for]="d1" /><mat-datepicker #d1 /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Eddig</label><mat-form-field><input matInput [matDatepicker]="d2" [(ngModel)]="to" /><mat-datepicker-toggle matIconSuffix [for]="d2" /><mat-datepicker #d2 /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Végrehajtó (felhasználónév)</label><mat-form-field><input matInput [(ngModel)]="actor" /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Művelet</label><mat-form-field><input matInput [(ngModel)]="action" placeholder="pl. TENANT_CREATE" /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Cél azonosító (pl. tenant kód)</label><mat-form-field><input matInput [(ngModel)]="targetId" /></mat-form-field></div>
      <button mat-flat-button (click)="load()"><mat-icon>search</mat-icon> Keresés</button>
    </div>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="time"><th mat-header-cell *matHeaderCellDef>Idő</th><td mat-cell *matCellDef="let r">{{ r.occurredAt | date: 'yyyy.MM.dd HH:mm:ss' }}</td></ng-container>
      <ng-container matColumnDef="actor"><th mat-header-cell *matHeaderCellDef>Végrehajtó</th><td mat-cell *matCellDef="let r">{{ r.actorName }} ({{ r.actorId }})</td></ng-container>
      <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef>Művelet</th><td mat-cell *matCellDef="let r">{{ r.action }}</td></ng-container>
      <ng-container matColumnDef="target"><th mat-header-cell *matHeaderCellDef>Cél</th><td mat-cell *matCellDef="let r">{{ r.targetType }} {{ r.targetId }}</td></ng-container>
      <ng-container matColumnDef="details"><th mat-header-cell *matHeaderCellDef>Részletek</th><td mat-cell *matCellDef="let r">@if (r.details) {<code>{{ r.details | json }}</code>}</td></ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
  `,
})
export class AdminAudit {
  private readonly api = inject(AdminApi);
  readonly cols = ['time', 'actor', 'action', 'target', 'details'];
  readonly rows = signal<Row[]>([]);
  from: Date | null = null;
  to: Date | null = null;
  actor = '';
  action = '';
  targetId = '';

  constructor() {
    this.load();
  }

  load(): void {
    const toNext = this.to ? new Date(this.to.getFullYear(), this.to.getMonth(), this.to.getDate() + 1) : null;
    this.api.get<Row[]>('audit', {
      from: this.from ? `${isoDate(this.from)}T00:00:00Z` : null, to: toNext ? `${isoDate(toNext)}T00:00:00Z` : null,
      actor: this.actor, action: this.action, targetId: this.targetId, limit: 200,
    }).subscribe((r) => this.rows.set(r));
  }
}
