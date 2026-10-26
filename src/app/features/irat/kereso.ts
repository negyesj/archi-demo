import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { ApiService } from '../../core/api';
import { isoDate, Page } from '../../core/util';
import { ALLAPOT_NEV, Allapot } from './models';

type Row = Record<string, unknown> & { _updatedAt?: string };

/**
 * Iratkereső: a felületen felvitt adatokra keres; a NoSQL (MongoDB) read modell szolgálja ki, jogosultságkezelten.
 * Az adat aszinkron frissül (jellemzően másodpercen belül).
 */
@Component({
  selector: 'app-kereso',
  imports: [FormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatDatepickerModule],
  template: `
    <h1>Iratkereső</h1>
    <div class="filters">
      <mat-form-field><mat-label>Iktatószám</mat-label><input matInput [(ngModel)]="f.iktatoszam" (keyup.enter)="load(0)" /></mat-form-field>
      <mat-form-field><mat-label>Állapot</mat-label>
        <mat-select [(ngModel)]="f.allapot"><mat-option [value]="null">Mind</mat-option>
          @for (a of allapotok; track a) { <mat-option [value]="a">{{ nev[a] }}</mat-option> }
        </mat-select></mat-form-field>
      <mat-form-field><mat-label>Beérkezés ettől</mat-label><input matInput [matDatepicker]="d1" [(ngModel)]="f.tol" /><mat-datepicker-toggle matIconSuffix [for]="d1" /><mat-datepicker #d1 /></mat-form-field>
      <mat-form-field><mat-label>Beérkezés eddig</mat-label><input matInput [matDatepicker]="d2" [(ngModel)]="f.ig" /><mat-datepicker-toggle matIconSuffix [for]="d2" /><mat-datepicker #d2 /></mat-form-field>
      <mat-form-field><mat-label>Partner</mat-label><input matInput [(ngModel)]="f.partner" /></mat-form-field>
      <mat-form-field><mat-label>Ügyintéző</mat-label><input matInput [(ngModel)]="f.ugyintezo" /></mat-form-field>
      <mat-form-field><mat-label>Tárgy</mat-label><input matInput [(ngModel)]="f.targy" /></mat-form-field>
      <mat-form-field><mat-label>Mellékletek száma</mat-label><input matInput type="number" [(ngModel)]="f.mellekletek" /></mat-form-field>
      <button mat-flat-button (click)="load(0)"><mat-icon>search</mat-icon> Keresés</button>
    </div>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="iktatoszam"><th mat-header-cell *matHeaderCellDef>Iktatószám</th><td mat-cell *matCellDef="let r">{{ r.iktatoszam }}</td></ng-container>
      <ng-container matColumnDef="allapot"><th mat-header-cell *matHeaderCellDef>Állapot</th><td mat-cell *matCellDef="let r">{{ allapotNev(r.allapot) }}</td></ng-container>
      <ng-container matColumnDef="erkezes"><th mat-header-cell *matHeaderCellDef>Beérkezés</th><td mat-cell *matCellDef="let r">{{ r.erkezesDatum }}</td></ng-container>
      <ng-container matColumnDef="partner"><th mat-header-cell *matHeaderCellDef>Partner</th>
        <td mat-cell *matCellDef="let r">{{ r.partnerNev }} @if (r.partnerAktualisNev && r.partnerAktualisNev !== r.partnerNev) { <small class="muted">(jelenleg: {{ r.partnerAktualisNev }})</small> }</td></ng-container>
      <ng-container matColumnDef="targy"><th mat-header-cell *matHeaderCellDef>Tárgy</th><td mat-cell *matCellDef="let r">{{ r.targy }}</td></ng-container>
      <ng-container matColumnDef="mellekletek"><th mat-header-cell *matHeaderCellDef>Mell.</th><td mat-cell *matCellDef="let r">{{ r.mellekletekSzama }}</td></ng-container>
      <ng-container matColumnDef="ugyintezo"><th mat-header-cell *matHeaderCellDef>Ügyintéző</th><td mat-cell *matCellDef="let r">{{ r.ugyintezoNev }}</td></ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
    <mat-paginator [length]="total()" [pageSize]="size" [pageIndex]="page()" [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" />
    <p class="muted">Az adatok aszinkron frissülnek a riport adatbázisban; a frissen rögzített tétel néhány másodperc múlva kereshető.</p>
  `,
})
export class Kereso {
  private readonly api = inject(ApiService);
  readonly nev = ALLAPOT_NEV;

  allapotNev(a: unknown): string {
    return ALLAPOT_NEV[a as Allapot] ?? String(a ?? '');
  }
  readonly allapotok: Allapot[] = ['ERKEZTETETT', 'BONTOTT', 'IKTATOTT'];
  readonly cols = ['iktatoszam', 'allapot', 'erkezes', 'partner', 'targy', 'mellekletek', 'ugyintezo'];
  readonly rows = signal<Row[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  size = 50;
  f = {
    iktatoszam: '', allapot: null as Allapot | null, tol: null as Date | null, ig: null as Date | null,
    partner: '', ugyintezo: '', targy: '', mellekletek: null as number | null,
  };

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.api.get<Page<Row>>('irat/kereso', {
      iktatoszam: this.f.iktatoszam, allapot: this.f.allapot, erkezesTol: isoDate(this.f.tol), erkezesIg: isoDate(this.f.ig),
      partner: this.f.partner, ugyintezo: this.f.ugyintezo, targy: this.f.targy, mellekletekSzama: this.f.mellekletek,
      page, size: this.size,
    }).subscribe((r) => {
      this.rows.set(r.items);
      this.total.set(r.total);
      this.page.set(r.page);
    });
  }

  onPage(e: PageEvent): void {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }
}
