import { Component, inject, signal } from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { ApiService } from '../../core/api';
import { isoDate, Page } from '../../core/util';

interface Change { field: string; from: unknown; to: unknown; }
interface AuditEvent {
  id: number; eventId: string; occurredAt: string; service: string; entity: string; entityId: string; action: string;
  version: number | null; actorId: string | null; actorName: string | null; actorType: string | null; traceId: string | null;
  changes: Change[] | null; after: unknown; before: unknown;
}

/** Audit napló kereső: minden CRUD esemény (aszinkron rögzítés), mezőszintű változásokkal és hibakóddal (traceId). */
@Component({
  selector: 'app-audit',
  imports: [DatePipe, JsonPipe, FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
    MatExpansionModule, MatPaginatorModule, MatDatepickerModule],
  template: `
    <h1>Audit napló</h1>
    <div class="filters">
      <div class="form-item"><label class="form-label">Időszak kezdete</label><mat-form-field><input matInput [matDatepicker]="dp1" [(ngModel)]="from" /><mat-datepicker-toggle matIconSuffix [for]="dp1" /><mat-datepicker #dp1 /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Időszak vége</label><mat-form-field><input matInput [matDatepicker]="dp2" [(ngModel)]="to" /><mat-datepicker-toggle matIconSuffix [for]="dp2" /><mat-datepicker #dp2 /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Szolgáltatás</label><mat-form-field><mat-select [(ngModel)]="service"><mat-option [value]="''">Mind</mat-option>
          @for (s of services; track s) { <mat-option [value]="s">{{ s }}</mat-option> }
        </mat-select></mat-form-field></div>
      <div class="form-item"><label class="form-label">Végrehajtó</label><mat-form-field><input matInput [(ngModel)]="actor" /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Művelet</label><mat-form-field><input matInput [(ngModel)]="action" placeholder="pl. IKTATAS" /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Szabad szöveg / azonosító</label><mat-form-field><input matInput [(ngModel)]="q" (keyup.enter)="load(0)" /></mat-form-field></div>
      <button mat-flat-button (click)="load(0)"><mat-icon>search</mat-icon> Keresés</button>
    </div>
    <p class="muted">{{ total() }} esemény</p>
    <mat-accordion>
      @for (e of rows(); track e.id) {
        <mat-expansion-panel>
          <mat-expansion-panel-header>
            <mat-panel-title>{{ e.occurredAt | date: 'yyyy.MM.dd HH:mm:ss' }} – {{ e.service }} / {{ e.entity }} – <strong>&nbsp;{{ e.action }}</strong></mat-panel-title>
            <mat-panel-description>{{ e.actorName ?? e.actorId }}</mat-panel-description>
          </mat-expansion-panel-header>
          <p>Azonosító: <code>{{ e.entityId }}</code> · verzió: {{ e.version }} · hibakód (traceId): <code>{{ e.traceId }}</code></p>
          @if (e.changes?.length) {
            <table class="simple">
              <tr><th>Mező</th><th>Előtte</th><th>Utána</th></tr>
              @for (c of e.changes; track c.field) {
                <tr><td>{{ c.field }}</td><td><code>{{ c.from | json }}</code></td><td><code>{{ c.to | json }}</code></td></tr>
              }
            </table>
          }
        </mat-expansion-panel>
      }
    </mat-accordion>
    <mat-paginator [length]="total()" [pageSize]="size" [pageIndex]="page()" [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" />
  `,
})
export class Audit {
  private readonly api = inject(ApiService);
  readonly services = ['iam', 'partner', 'irat', 'webhook'];
  readonly rows = signal<AuditEvent[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  size = 50;
  from: Date | null = null;
  to: Date | null = null;
  service = '';
  actor = '';
  action = '';
  q = '';

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    const toNext = this.to ? new Date(this.to.getFullYear(), this.to.getMonth(), this.to.getDate() + 1) : null;
    this.api.get<Page<AuditEvent>>('audit/events', {
      from: this.from ? `${isoDate(this.from)}T00:00:00Z` : null,
      to: toNext ? `${isoDate(toNext)}T00:00:00Z` : null,
      service: this.service, actor: this.actor, action: this.action, q: this.q, page, size: this.size,
    }).subscribe((p) => {
      this.rows.set(p.items);
      this.total.set(p.total);
      this.page.set(p.page);
    });
  }

  onPage(e: PageEvent): void {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }
}
