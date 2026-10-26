import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { ApiService } from '../../core/api';
import { NotifyService } from '../../core/notify';
import { UserContextService } from '../../core/context';
import { isoDate } from '../../core/util';
import { PartnerPicker, PartnerSummary, UserPicker, UserSummary } from '../../shared/pickers';
import { Kuldemeny } from './models';

/**
 * Érkeztetés: beérkezés dátuma + partner (Partner szolgáltatásból). Az "egy lépésben" opcióval a bontás és az
 * iktatás adatai is megadhatók: egyetlen tranzakció, de az auditban három külön esemény.
 */
@Component({
  selector: 'app-erkeztetes',
  imports: [FormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatCheckboxModule,
    MatDatepickerModule, PartnerPicker, UserPicker],
  template: `
    <h1>Érkeztetés</h1>
    <mat-card class="form-card">
      <mat-card-content class="form-col">
        <mat-form-field><mat-label>Beérkezés dátuma</mat-label>
          <input matInput [matDatepicker]="d1" [(ngModel)]="erkezes" required [max]="today" />
          <mat-datepicker-toggle matIconSuffix [for]="d1" /><mat-datepicker #d1 /></mat-form-field>
        <app-partner-picker label="Küldő partner" [required]="true" [(value)]="partner" />
        <mat-form-field><mat-label>Tárgy</mat-label><input matInput [(ngModel)]="targy" maxlength="500" /></mat-form-field>
        @if (canAll) {
          <mat-checkbox [(ngModel)]="egyben">Bontás és iktatás egy lépésben</mat-checkbox>
        }
        @if (egyben) {
          <mat-form-field><mat-label>Bontás dátuma</mat-label>
            <input matInput [matDatepicker]="d2" [(ngModel)]="bontas" required [min]="erkezes" />
            <mat-datepicker-toggle matIconSuffix [for]="d2" /><mat-datepicker #d2 /></mat-form-field>
          <mat-form-field><mat-label>Mellékletek száma</mat-label><input matInput type="number" min="0" [(ngModel)]="mellekletek" required /></mat-form-field>
          <app-user-picker label="Ügyintéző" [required]="true" [(value)]="ugyintezo" />
        }
      </mat-card-content>
      <mat-card-actions align="end">
        <button mat-flat-button [disabled]="!valid() || busy()" (click)="save()"><mat-icon>move_to_inbox</mat-icon>
          {{ egyben ? 'Érkeztetés, bontás és iktatás' : 'Érkeztetés' }}</button>
      </mat-card-actions>
    </mat-card>
    @if (last(); as k) {
      <mat-card class="form-card result">
        <mat-card-content>
          <strong>{{ k.iktatoszam ? 'Iktatva: ' + k.iktatoszam : 'Érkeztetve' }}</strong> – {{ k.partnerNev }}, {{ k.erkezesDatum }}
          @if (k.ugyintezoNev) { – ügyintéző: {{ k.ugyintezoNev }} }
        </mat-card-content>
      </mat-card>
    }
  `,
})
export class Erkeztetes {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  readonly canAll = inject(UserContextService).has('irat.bontas') && inject(UserContextService).has('irat.iktatas');
  readonly today = new Date();
  readonly partner = signal<PartnerSummary | null>(null);
  readonly ugyintezo = signal<UserSummary | null>(null);
  readonly busy = signal(false);
  readonly last = signal<Kuldemeny | null>(null);
  erkezes: Date = new Date();
  targy = '';
  egyben = false;
  bontas: Date = new Date();
  mellekletek: number | null = 0;

  valid(): boolean {
    if (!this.erkezes || !this.partner()) {
      return false;
    }
    return !this.egyben || (!!this.bontas && this.mellekletek !== null && this.mellekletek >= 0 && !!this.ugyintezo());
  }

  save(): void {
    this.busy.set(true);
    const base = { erkezesDatum: isoDate(this.erkezes), partnerId: this.partner()!.id, targy: this.targy || null };
    const req = this.egyben
      ? this.api.post<Kuldemeny>('irat/kuldemenyek/egylepeses', {
          ...base, bontasDatum: isoDate(this.bontas), mellekletekSzama: this.mellekletek, ugyintezoId: this.ugyintezo()!.id,
        })
      : this.api.post<Kuldemeny>('irat/kuldemenyek', base);
    req.subscribe({
      next: (k) => {
        this.last.set(k);
        this.notify.ok(k.iktatoszam ? `Iktatószám: ${k.iktatoszam}` : 'Küldemény érkeztetve');
        this.targy = '';
        this.busy.set(false);
      },
      error: () => this.busy.set(false),
    });
  }
}
