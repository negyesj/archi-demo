import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { ApiService } from '../../core/api';
import { NotifyService } from '../../core/notify';
import { UserContextService } from '../../core/context';
import { isoDate } from '../../core/util';
import { UserPicker, UserSummary } from '../../shared/pickers';
import { ALLAPOT_NEV, Allapot, Kuldemeny } from './models';

@Component({
  selector: 'app-bontas-dialog',
  imports: [FormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatDatepickerModule],
  template: `
    <h2 mat-dialog-title>Bontás – {{ k.partnerNev }}</h2>
    <mat-dialog-content class="form-col">
      <div class="form-item"><label class="form-label">Bontás dátuma</label><mat-form-field><input matInput [matDatepicker]="d" [(ngModel)]="datum" required [min]="min" />
        <mat-datepicker-toggle matIconSuffix [for]="d" /><mat-datepicker #d /></mat-form-field></div>
      <div class="form-item"><label class="form-label">Mellékletek száma</label><mat-form-field><input matInput type="number" min="0" [(ngModel)]="db" required /></mat-form-field></div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Mégse</button>
      <button mat-flat-button [disabled]="!datum || db === null || db < 0" (click)="save()">Bontás</button>
    </mat-dialog-actions>
  `,
})
export class BontasDialog {
  readonly k = inject<Kuldemeny>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<BontasDialog>);
  private readonly api = inject(ApiService);
  readonly min = new Date(this.k.erkezesDatum);
  datum: Date = new Date();
  db: number | null = 0;

  save(): void {
    this.api.post<Kuldemeny>(`irat/kuldemenyek/${this.k.id}/bontas`, { bontasDatum: isoDate(this.datum), mellekletekSzama: this.db, version: this.k.version })
      .subscribe((r) => this.ref.close(r));
  }
}

@Component({
  selector: 'app-iktatas-dialog',
  imports: [MatDialogModule, MatButtonModule, UserPicker],
  template: `
    <h2 mat-dialog-title>Iktatás – {{ k.partnerNev }}</h2>
    <mat-dialog-content>
      <p>Beérkezés: {{ k.erkezesDatum }}, bontás: {{ k.bontasDatum }}, mellékletek: {{ k.mellekletekSzama }}</p>
      <app-user-picker label="Ügyintéző" [required]="true" [(value)]="ugyintezo" />
      <p class="muted">Az iktatószámot a rendszer adja (egyedi, sorfolytonos).</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Mégse</button>
      <button mat-flat-button [disabled]="!ugyintezo()" (click)="save()">Iktatás</button>
    </mat-dialog-actions>
  `,
})
export class IktatasDialog {
  readonly k = inject<Kuldemeny>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<IktatasDialog>);
  private readonly api = inject(ApiService);
  readonly ugyintezo = signal<UserSummary | null>(null);

  save(): void {
    this.api.post<Kuldemeny>(`irat/kuldemenyek/${this.k.id}/iktatas`, { ugyintezoId: this.ugyintezo()!.id, version: this.k.version })
      .subscribe((r) => this.ref.close(r));
  }
}

/** Munkalista: érkeztetett küldemények bontása, bontottak iktatása. */
@Component({
  selector: 'app-feldolgozas',
  imports: [FormsModule, MatTableModule, MatButtonToggleModule, MatButtonModule, MatIconModule],
  template: `
    <h1>Bontás, iktatás</h1>
    <mat-button-toggle-group [(ngModel)]="allapot" (change)="load()">
      <mat-button-toggle value="ERKEZTETETT">Bontásra vár</mat-button-toggle>
      <mat-button-toggle value="BONTOTT">Iktatásra vár</mat-button-toggle>
      <mat-button-toggle value="IKTATOTT">Iktatott</mat-button-toggle>
    </mat-button-toggle-group>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="erkezes"><th mat-header-cell *matHeaderCellDef>Beérkezés</th><td mat-cell *matCellDef="let k">{{ k.erkezesDatum }}</td></ng-container>
      <ng-container matColumnDef="partner"><th mat-header-cell *matHeaderCellDef>Partner</th><td mat-cell *matCellDef="let k">{{ k.partnerNev }}</td></ng-container>
      <ng-container matColumnDef="targy"><th mat-header-cell *matHeaderCellDef>Tárgy</th><td mat-cell *matCellDef="let k">{{ k.targy }}</td></ng-container>
      <ng-container matColumnDef="allapot"><th mat-header-cell *matHeaderCellDef>Állapot</th><td mat-cell *matCellDef="let k">{{ allapotNev(k.allapot) }}</td></ng-container>
      <ng-container matColumnDef="iktatoszam"><th mat-header-cell *matHeaderCellDef>Iktatószám</th><td mat-cell *matCellDef="let k">{{ k.iktatoszam }}</td></ng-container>
      <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let k" class="actions">
          @if (k.allapot === 'ERKEZTETETT' && canBont) { <button mat-stroked-button (click)="bont(k)">Bontás</button> }
          @if (k.allapot === 'BONTOTT' && canIktat) { <button mat-flat-button (click)="iktat(k)">Iktatás</button> }
        </td></ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
  `,
})
export class Feldolgozas {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  private readonly ctx = inject(UserContextService);
  readonly canBont = this.ctx.has('irat.bontas');
  readonly canIktat = this.ctx.has('irat.iktatas');
  readonly nev = ALLAPOT_NEV;

  allapotNev(a: unknown): string {
    return ALLAPOT_NEV[a as Allapot] ?? String(a ?? '');
  }
  readonly cols = ['erkezes', 'partner', 'targy', 'allapot', 'iktatoszam', 'actions'];
  readonly rows = signal<Kuldemeny[]>([]);
  allapot: Allapot = 'ERKEZTETETT';

  constructor() {
    this.load();
  }

  load(): void {
    this.api.get<Kuldemeny[]>('irat/kuldemenyek', { allapot: this.allapot, limit: 200 }).subscribe((r) => this.rows.set(r));
  }

  bont(k: Kuldemeny): void {
    this.dialog.open(BontasDialog, { data: k, width: '420px' }).afterClosed().subscribe((r) => {
      if (r) {
        this.notify.ok('Küldemény bontva');
        this.load();
      }
    });
  }

  iktat(k: Kuldemeny): void {
    this.dialog.open(IktatasDialog, { data: k, width: '480px' }).afterClosed().subscribe((r?: Kuldemeny) => {
      if (r) {
        this.notify.ok(`Iktatószám: ${r.iktatoszam}`);
        this.load();
      }
    });
  }
}
