import { Component, computed, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { KIND_COLOR, KIND_LABEL, TrailDetail, TrailStep } from './models';

interface Milestone { label: string; ms: number | null; hint: string; }

/** Egy művelet lépései vízesés nézetben: szolgáltatás, típus, érintett adatbázis/topic, időtartam, várakozás. */
@Component({
  selector: 'app-trail-detail',
  imports: [DatePipe, DecimalPipe, MatDialogModule, MatButtonModule, MatTooltipModule],
  template: `
    <h2 mat-dialog-title>{{ root()?.operation ?? 'Művelet' }}</h2>
    <mat-dialog-content>
      <div class="trail-head">
        <span>Kezdés: <b>{{ d.startedAt | date: 'yyyy.MM.dd HH:mm:ss.SSS' }}</b></span>
        <span>Felhasználó: <b>{{ root()?.user ?? '–' }}</b></span>
        <span>Válaszidő: <b>{{ d.durationMs | number }} ms</b></span>
        <span>Teljes lefutás (aszinkron lépésekkel): <b>{{ d.completeMs ?? 0 | number }} ms</b></span>
        <span class="muted">traceId: <code>{{ d.traceId }}</code></span>
      </div>
      <div class="milestones">
        @for (m of milestones(); track m.label) {
          <div class="milestone" [matTooltip]="m.hint">
            <div class="ms-label">{{ m.label }}</div>
            <div class="ms-value">{{ m.ms === null ? '–' : (m.ms | number) + ' ms' }}</div>
          </div>
        }
      </div>
      <table class="simple trail-steps">
        <tr><th>Kezdés</th><th>Szolgáltatás</th><th>Típus</th><th>Művelet</th><th>Érintett (DB / topic)</th>
          <th class="num">Idő</th><th class="num">Várakozás</th><th class="num">E2E</th><th class="bar-col">Idővonal</th></tr>
        @for (s of d.steps; track s.spanId) {
          <tr [class.err]="s.status === 'ERROR'" [matTooltip]="s.detail ?? ''" matTooltipClass="trail-tip">
            <td class="num">+{{ s.offsetMs | number }}</td>
            <td>{{ s.service }}</td>
            <td><span class="kind" [style.background]="color(s.kind)">{{ label(s.kind) }}</span></td>
            <td class="op">{{ s.operation }}</td>
            <td>{{ s.target }}</td>
            <td class="num">{{ s.durationMs | number }} ms</td>
            <td class="num">{{ s.queueMs === null ? '' : (s.queueMs | number) + ' ms' }}</td>
            <td class="num">{{ s.e2eMs === null ? '' : (s.e2eMs | number) + ' ms' }}</td>
            <td class="bar-col"><div class="bar-track">
              @if (s.queueMs) { <div class="bar queue" [style.left.%]="left(s.offsetMs - s.queueMs)" [style.width.%]="width(s.queueMs)"></div> }
              <div class="bar" [style.left.%]="left(s.offsetMs)" [style.width.%]="width(s.durationMs)" [style.background]="color(s.kind)"></div>
            </div></td>
          </tr>
        }
      </table>
      <p class="muted">A sorra mutatva látszik a részlet (HTTP státusz, SQL utasítás, hibaüzenet). A szaggatott sáv a várakozás
        (outbox → Kafka, Kafka → fogyasztó); az E2E az üzleti művelettől a lépés végéig eltelt idő.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button mat-button mat-dialog-close>Bezárás</button></mat-dialog-actions>
  `,
  styles: [`
    .trail-head { display: flex; flex-wrap: wrap; gap: 6px 24px; margin-bottom: 12px; font-size: 13px; }
    .milestones { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
    .milestone { background: #fff; border-radius: 6px; box-shadow: 0 1px 3px rgba(0,0,0,.08); padding: 8px 12px; min-width: 150px; }
    .ms-label { font-size: 11px; color: #757575; }
    .ms-value { font-size: 16px; font-weight: 700; }
    .trail-steps { width: 100%; font-size: 12px; }
    .trail-steps td, .trail-steps th { padding: 4px 8px; white-space: nowrap; }
    .trail-steps .op { max-width: 320px; overflow: hidden; text-overflow: ellipsis; }
    .trail-steps tr.err td { color: #ED342F; }
    .num { text-align: right; }
    .kind { color: #fff; border-radius: 10px; padding: 1px 8px; font-size: 11px; font-weight: 600; }
    .bar-col { width: 260px; min-width: 200px; }
    .bar-track { position: relative; height: 12px; background: #f5f5f5; border-radius: 2px; }
    .bar { position: absolute; top: 1px; height: 10px; border-radius: 2px; min-width: 2px; }
    .bar.queue { background: repeating-linear-gradient(90deg, #c4c4c4 0 4px, transparent 4px 7px); }
  `],
})
export class TrailDetailDialog {
  readonly d = inject<TrailDetail>(MAT_DIALOG_DATA);
  readonly root = computed(() => this.d.steps.find((s) => s.root) ?? this.d.steps[0] ?? null);
  private readonly total = Math.max(1, this.d.completeMs ?? this.d.durationMs);

  readonly milestones = computed<Milestone[]>(() => {
    const s = this.d.steps;
    const maxE2e = (pred: (x: TrailStep) => boolean) => {
      const v = s.filter(pred).map((x) => x.e2eMs ?? 0);
      return v.length ? Math.max(...v) : null;
    };
    const sum = (kind: string) => {
      const v = s.filter((x) => x.kind === kind);
      return v.length ? v.reduce((a, x) => a + x.durationMs, 0) : null;
    };
    const outbox = s.filter((x) => x.kind === 'KAFKA_PUBLISH').map((x) => x.queueMs ?? 0);
    return [
      { label: 'SQL összesen', ms: sum('SQL'), hint: 'Az összes SQL utasítás ideje a tenant PostgreSQL adatbázisában' },
      { label: 'Outbox → Kafka', ms: outbox.length ? Math.max(...outbox) : null, hint: 'Várakozás az outbox táblában a Kafkára küldésig' },
      { label: 'Kikerült a NoSQL-be', ms: maxE2e((x) => x.kind === 'MONGO'), hint: 'Az üzleti művelettől a riport (MongoDB) dokumentum írásáig' },
      { label: 'Audit naplóba írva', ms: maxE2e((x) => x.kind === 'KAFKA_CONSUME' && x.service === 'audit'), hint: 'Az üzleti művelettől az audit sor beírásáig' },
      { label: 'Webhook kézbesítve', ms: maxE2e((x) => x.kind === 'WEBHOOK'), hint: 'Az üzleti művelettől a webhook kézbesítéséig' },
    ];
  });

  label(kind: string): string {
    return KIND_LABEL[kind] ?? kind;
  }

  color(kind: string): string {
    return KIND_COLOR[kind] ?? '#9e9e9e';
  }

  left(ms: number): number {
    return Math.max(0, Math.min(100, (ms / this.total) * 100));
  }

  width(ms: number): number {
    return Math.max(0.4, Math.min(100, (ms / this.total) * 100));
  }
}
