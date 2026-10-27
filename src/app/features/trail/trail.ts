import { Component, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ApiService } from '../../core/api';
import { Page } from '../../core/util';
import { KIND_COLOR, KIND_LABEL, KINDS, TrailDetail, TrailOperation, TrailStat } from './models';
import { TrailDetailDialog } from './trail-detail';

interface Range { label: string; hours: number; }

/**
 * Műveleti trail: minden művelet (gateway kérés) lépései a szolgáltatásokon, adatbázisokon és Kafkán át,
 * időméréssel; valamint órás/napi átlagok műveletenként. Jogosultság: trail.read.
 */
@Component({
  selector: 'app-trail',
  imports: [DatePipe, DecimalPipe, FormsModule, MatTabsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatCheckboxModule, MatButtonModule, MatIconModule, MatTooltipModule, MatDialogModule],
  template: `
    <h1>Műveleti trail</h1>
    <mat-tab-group animationDuration="0ms" (selectedIndexChange)="tab.set($event)">
      <mat-tab label="Műveletek">
        <div class="filters tab-filters">
          <div class="form-item"><label class="form-label">Időszak</label><mat-form-field><mat-select [(ngModel)]="opRange">
            @for (r of opRanges; track r.hours) { <mat-option [value]="r.hours">{{ r.label }}</mat-option> }
          </mat-select></mat-form-field></div>
          <div class="form-item"><label class="form-label">Érintett szolgáltatás</label><mat-form-field><mat-select [(ngModel)]="opService">
            <mat-option [value]="''">Mind</mat-option>
            @for (s of services(); track s) { <mat-option [value]="s">{{ s }}</mat-option> }
          </mat-select></mat-form-field></div>
          <div class="form-item"><label class="form-label">Művelet</label><mat-form-field><input matInput [(ngModel)]="opQ" placeholder="pl. iktatas" (keyup.enter)="loadOps(0)" /></mat-form-field></div>
          <div class="form-item"><label class="form-label">Felhasználó</label><mat-form-field><input matInput [(ngModel)]="opUser" (keyup.enter)="loadOps(0)" /></mat-form-field></div>
          <div class="form-item"><label class="form-label">Válaszidő legalább (ms)</label><mat-form-field><input matInput type="number" min="0" [(ngModel)]="opMinMs" /></mat-form-field></div>
          <mat-checkbox [(ngModel)]="opErrors">Csak hibás</mat-checkbox>
          <button mat-flat-button (click)="loadOps(0)"><mat-icon>search</mat-icon> Keresés</button>
        </div>
        <table mat-table [dataSource]="ops()" class="full">
          <ng-container matColumnDef="time"><th mat-header-cell *matHeaderCellDef>Időpont</th>
            <td mat-cell *matCellDef="let o" class="nowrap">{{ o.startedAt | date: 'MM.dd HH:mm:ss' }}</td></ng-container>
          <ng-container matColumnDef="operation"><th mat-header-cell *matHeaderCellDef>Művelet</th>
            <td mat-cell *matCellDef="let o" class="op-cell" [matTooltip]="o.operation">{{ o.operation }}</td></ng-container>
          <ng-container matColumnDef="user"><th mat-header-cell *matHeaderCellDef>Felhasználó</th>
            <td mat-cell *matCellDef="let o">{{ o.user }}</td></ng-container>
          <ng-container matColumnDef="duration"><th mat-header-cell *matHeaderCellDef class="num">Válaszidő</th>
            <td mat-cell *matCellDef="let o" class="num">{{ o.durationMs | number }} ms</td></ng-container>
          <ng-container matColumnDef="complete"><th mat-header-cell *matHeaderCellDef class="num" matTooltip="Az aszinkron lépésekkel (Kafka, NoSQL, audit, webhook) együtt">Teljes lefutás</th>
            <td mat-cell *matCellDef="let o" class="num">{{ o.completeMs === null ? '' : (o.completeMs | number) + ' ms' }}</td></ng-container>
          <ng-container matColumnDef="kinds"><th mat-header-cell *matHeaderCellDef>Idő típusonként</th>
            <td mat-cell *matCellDef="let o" class="kinds-cell">
              @for (k of kindsOf(o); track k.kind) {
                <span class="kind-chip" [style.border-color]="color(k.kind)" [matTooltip]="label(k.kind)">{{ short(k.kind) }} {{ k.ms | number }}</span>
              }
            </td></ng-container>
          <ng-container matColumnDef="services"><th mat-header-cell *matHeaderCellDef>Szolgáltatás</th>
            <td mat-cell *matCellDef="let o" class="muted services-cell" [matTooltip]="o.services.join(', ')">{{ o.services.length }} db</td></ng-container>
          <ng-container matColumnDef="steps"><th mat-header-cell *matHeaderCellDef class="num">Lépés</th>
            <td mat-cell *matCellDef="let o" class="num">{{ o.steps }}</td></ng-container>
          <ng-container matColumnDef="status"><th mat-header-cell *matHeaderCellDef>Állapot</th>
            <td mat-cell *matCellDef="let o">
              <span class="badge" [class.badge-ok]="o.status === 'OK' && !o.errors" [class.badge-err]="o.status !== 'OK' || o.errors">
                {{ o.status === 'OK' && !o.errors ? 'Rendben' : 'Hiba' }}</span>
            </td></ng-container>
          <tr mat-header-row *matHeaderRowDef="opCols; sticky: true"></tr>
          <tr mat-row *matRowDef="let row; columns: opCols" (click)="open(row)" class="clickable"></tr>
        </table>
        @if (ops().length === 0) { <p class="empty muted">Nincs művelet a megadott időszakban.</p> }
        <mat-paginator [length]="opTotal()" [pageSize]="opSize" [pageIndex]="opPage()" [pageSizeOptions]="[25, 50, 100, 200]" (page)="onOpPage($event)" />
      </mat-tab>

      <mat-tab label="Összesítő">
        <div class="filters tab-filters">
          <div class="form-item"><label class="form-label">Bontás</label><mat-form-field><mat-select [(ngModel)]="bucket">
            <mat-option value="hour">Óránként</mat-option><mat-option value="day">Naponként</mat-option>
          </mat-select></mat-form-field></div>
          <div class="form-item"><label class="form-label">Időszak</label><mat-form-field><mat-select [(ngModel)]="statRange">
            @for (r of statRanges; track r.hours) { <mat-option [value]="r.hours">{{ r.label }}</mat-option> }
          </mat-select></mat-form-field></div>
          <div class="form-item"><label class="form-label">Lépés típusa</label><mat-form-field><mat-select [(ngModel)]="statKind">
            <mat-option [value]="''">Mind</mat-option>
            @for (k of kinds; track k) { <mat-option [value]="k">{{ label(k) }}</mat-option> }
          </mat-select></mat-form-field></div>
          <div class="form-item"><label class="form-label">Szolgáltatás</label><mat-form-field><mat-select [(ngModel)]="statService">
            <mat-option [value]="''">Mind</mat-option>
            @for (s of services(); track s) { <mat-option [value]="s">{{ s }}</mat-option> }
          </mat-select></mat-form-field></div>
          <div class="form-item"><label class="form-label">Művelet</label><mat-form-field><input matInput [(ngModel)]="statQ" (keyup.enter)="loadStats()" /></mat-form-field></div>
          <button mat-flat-button (click)="loadStats()"><mat-icon>search</mat-icon> Keresés</button>
        </div>
        <table mat-table [dataSource]="stats()" class="full">
          <ng-container matColumnDef="bucket"><th mat-header-cell *matHeaderCellDef>Időszak</th>
            <td mat-cell *matCellDef="let r">{{ r.bucket | date: bucket === 'day' ? 'yyyy.MM.dd' : 'MM.dd HH:00' }}</td></ng-container>
          <ng-container matColumnDef="service"><th mat-header-cell *matHeaderCellDef>Szolgáltatás</th>
            <td mat-cell *matCellDef="let r">{{ r.service }}</td></ng-container>
          <ng-container matColumnDef="kind"><th mat-header-cell *matHeaderCellDef>Típus</th>
            <td mat-cell *matCellDef="let r"><span class="kind-chip" [style.border-color]="color(r.kind)">{{ label(r.kind) }}</span></td></ng-container>
          <ng-container matColumnDef="operation"><th mat-header-cell *matHeaderCellDef>Művelet</th>
            <td mat-cell *matCellDef="let r" class="op-cell" [matTooltip]="r.operation">{{ r.operation }}</td></ng-container>
          <ng-container matColumnDef="count"><th mat-header-cell *matHeaderCellDef class="num">Darab</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.count | number }}</td></ng-container>
          <ng-container matColumnDef="avg"><th mat-header-cell *matHeaderCellDef class="num">Átlag</th>
            <td mat-cell *matCellDef="let r" class="num"><b>{{ r.avgMs | number: '1.0-1' }} ms</b></td></ng-container>
          <ng-container matColumnDef="max"><th mat-header-cell *matHeaderCellDef class="num">Max</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.maxMs | number }} ms</td></ng-container>
          <ng-container matColumnDef="queue"><th mat-header-cell *matHeaderCellDef class="num" matTooltip="Outbox → Kafka, illetve Kafka → fogyasztó várakozás átlaga">Átl. várakozás</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.avgQueueMs === null ? '' : (r.avgQueueMs | number: '1.0-1') + ' ms' }}</td></ng-container>
          <ng-container matColumnDef="e2e"><th mat-header-cell *matHeaderCellDef class="num" matTooltip="Az üzleti művelettől a lépés végéig (pl. mikor került ki a NoSQL-be, mikor íródott az audit)">Átl. E2E</th>
            <td mat-cell *matCellDef="let r" class="num">{{ r.avgE2eMs === null ? '' : (r.avgE2eMs | number: '1.0-1') + ' ms' }}</td></ng-container>
          <ng-container matColumnDef="errors"><th mat-header-cell *matHeaderCellDef class="num">Hiba</th>
            <td mat-cell *matCellDef="let r" class="num" [class.error-text]="r.errors > 0">{{ r.errors || '' }}</td></ng-container>
          <tr mat-header-row *matHeaderRowDef="statCols; sticky: true"></tr>
          <tr mat-row *matRowDef="let row; columns: statCols"></tr>
        </table>
        @if (stats().length === 0) { <p class="empty muted">Nincs adat a megadott szűrésre.</p> }
      </mat-tab>
    </mat-tab-group>
  `,
  styles: [`
    .tab-filters { margin-top: 8px; }
    .num { text-align: right; }
    .nowrap { white-space: nowrap; }
    .kinds-cell { min-width: 180px; max-width: 220px; }
    .services-cell { max-width: 90px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .op-cell { max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .kind-chip { display: inline-block; border: 1px solid; border-radius: 10px; padding: 0 6px; margin: 1px 4px 1px 0;
      font-size: 11px; line-height: 16px; white-space: nowrap; }
    .clickable { cursor: pointer; }
    .empty { background: #fff; padding: 12px; margin: 0; }
  `],
})
export class Trail {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);

  readonly kinds = KINDS;
  readonly tab = signal(0);
  readonly services = signal<string[]>([]);

  readonly opRanges: Range[] = [
    { label: 'Utolsó 1 óra', hours: 1 }, { label: 'Utolsó 6 óra', hours: 6 }, { label: 'Utolsó 24 óra', hours: 24 },
    { label: 'Utolsó 3 nap', hours: 72 }, { label: 'Utolsó 7 nap', hours: 168 }, { label: 'Utolsó 14 nap', hours: 336 },
  ];
  readonly statRanges: Range[] = [
    { label: 'Utolsó 24 óra', hours: 24 }, { label: 'Utolsó 7 nap', hours: 168 }, { label: 'Utolsó 30 nap', hours: 720 },
    { label: 'Utolsó 90 nap', hours: 2160 }, { label: 'Utolsó 13 hónap', hours: 9504 },
  ];

  readonly opCols = ['time', 'operation', 'user', 'duration', 'complete', 'kinds', 'services', 'steps', 'status'];
  readonly statCols = ['bucket', 'service', 'kind', 'operation', 'count', 'avg', 'max', 'queue', 'e2e', 'errors'];

  readonly ops = signal<TrailOperation[]>([]);
  readonly opTotal = signal(0);
  readonly opPage = signal(0);
  opSize = 50;
  opRange = 24;
  opService = '';
  opQ = '';
  opUser = '';
  opMinMs: number | null = null;
  opErrors = false;

  readonly stats = signal<TrailStat[]>([]);
  bucket: 'hour' | 'day' = 'hour';
  statRange = 24;
  statKind = '';
  statService = '';
  statQ = '';

  constructor() {
    this.api.get<{ services: string[]; kinds: string[] }>('audit/trail/facets').subscribe((f) => this.services.set(f.services));
    this.loadOps(0);
    this.loadStats();
  }

  private since(hours: number): string {
    return new Date(Date.now() - hours * 3600_000).toISOString();
  }

  loadOps(page: number): void {
    this.api.get<Page<TrailOperation>>('audit/trail/operations', {
      from: this.since(this.opRange), service: this.opService, q: this.opQ, user: this.opUser,
      minMs: this.opMinMs, errorsOnly: this.opErrors || null, page, size: this.opSize,
    }).subscribe((p) => {
      this.ops.set(p.items);
      this.opTotal.set(p.total);
      this.opPage.set(p.page);
    });
  }

  onOpPage(e: PageEvent): void {
    this.opSize = e.pageSize;
    this.loadOps(e.pageIndex);
  }

  loadStats(): void {
    if (this.bucket === 'day' && this.statRange < 168) {
      this.statRange = 168;
    }
    this.api.get<TrailStat[]>('audit/trail/stats', {
      bucket: this.bucket, from: this.since(this.statRange), service: this.statService, kind: this.statKind, q: this.statQ,
    }).subscribe((r) => this.stats.set(r));
  }

  open(o: TrailOperation): void {
    this.api.get<TrailDetail>(`audit/trail/operations/${o.traceId}`).subscribe((d) =>
      this.dialog.open(TrailDetailDialog, { data: d, width: '1180px', maxWidth: '96vw' }));
  }

  kindsOf(o: TrailOperation): { kind: string; ms: number }[] {
    return KINDS.filter((k) => k !== 'GATEWAY' && o.byKind[k] !== undefined).map((k) => ({ kind: k, ms: o.byKind[k] }));
  }

  label(kind: string): string {
    return KIND_LABEL[kind] ?? kind;
  }

  short(kind: string): string {
    return ({ HTTP: 'HTTP', SQL: 'SQL', KAFKA_PUBLISH: 'K→', KAFKA_CONSUME: '→K', MONGO: 'Mongo', WEBHOOK: 'WH' } as Record<string, string>)[kind] ?? kind;
  }

  color(kind: string): string {
    return KIND_COLOR[kind] ?? '#9e9e9e';
  }
}
