import { Component, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of, catchError } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../core/api';

export interface PartnerSummary {
  id: string;
  name: string;
  taxNumber: string | null;
  city: string | null;
  address: string | null;
  active: boolean;
}

export interface UserSummary {
  id: string;
  username: string;
  fullName: string;
  email: string | null;
  active: boolean;
}

/** Partner választó autocomplete-tel a Partner szolgáltatásból (más szolgáltatások ezt építik be). */
@Component({
  selector: 'app-partner-picker',
  imports: [FormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  template: `
    <div class="form-item full"><label class="form-label">{{ label() }}</label><mat-form-field>
      <input matInput [ngModel]="text()" (ngModelChange)="onType($event)" [matAutocomplete]="auto"
             [required]="required()" placeholder="Név, adószám vagy település" />
      <mat-autocomplete #auto="matAutocomplete" [displayWith]="display" (optionSelected)="select($event.option.value)">
        @for (p of options(); track p.id) {
          <mat-option [value]="p">
            <strong>{{ p.name }}</strong>
            <small class="muted"> {{ p.taxNumber ?? '' }} {{ p.address ?? '' }}</small>
          </mat-option>
        }
      </mat-autocomplete>
      @if (value()) { <mat-hint>{{ value()!.address }}</mat-hint> }
    </mat-form-field></div>
  `,
})
export class PartnerPicker {
  private readonly api = inject(ApiService);
  readonly label = input('Partner');
  readonly required = input(false);
  readonly value = model<PartnerSummary | null>(null);
  readonly options = signal<PartnerSummary[]>([]);
  readonly text = signal<string | PartnerSummary>('');
  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => (q.length < 2 ? of([]) : this.api.get<PartnerSummary[]>('partner/partners/lookup', { q }).pipe(catchError(() => of([]))))),
        takeUntilDestroyed(),
      )
      .subscribe((list) => this.options.set(list));
  }

  display = (p: PartnerSummary | string | null): string => (typeof p === 'string' ? p : (p?.name ?? ''));

  onType(v: string | PartnerSummary): void {
    this.text.set(v);
    if (typeof v === 'string') {
      this.value.set(null);
      this.search$.next(v.trim());
    }
  }

  select(p: PartnerSummary): void {
    this.value.set(p);
    this.text.set(p);
  }
}

/** Felhasználó (pl. ügyintéző) választó az IAM szolgáltatásból. */
@Component({
  selector: 'app-user-picker',
  imports: [FormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  template: `
    <div class="form-item full"><label class="form-label">{{ label() }}</label><mat-form-field>
      <input matInput [ngModel]="text()" (ngModelChange)="onType($event)" [matAutocomplete]="auto"
             [required]="required()" placeholder="Név vagy felhasználónév" />
      <mat-autocomplete #auto="matAutocomplete" [displayWith]="display" (optionSelected)="select($event.option.value)">
        @for (u of options(); track u.id) {
          <mat-option [value]="u">{{ u.fullName }} <small class="muted">({{ u.username }})</small></mat-option>
        }
      </mat-autocomplete>
    </mat-form-field></div>
  `,
})
export class UserPicker {
  private readonly api = inject(ApiService);
  readonly label = input('Felhasználó');
  readonly required = input(false);
  readonly value = model<UserSummary | null>(null);
  readonly options = signal<UserSummary[]>([]);
  readonly text = signal<string | UserSummary>('');
  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => this.api.get<UserSummary[]>('iam/users/lookup', { q }).pipe(catchError(() => of([])))),
        takeUntilDestroyed(),
      )
      .subscribe((list) => this.options.set(list));
  }

  display = (u: UserSummary | string | null): string => (typeof u === 'string' ? u : (u?.fullName ?? ''));

  onType(v: string | UserSummary): void {
    this.text.set(v);
    if (typeof v === 'string') {
      this.value.set(null);
      this.search$.next(v.trim());
    }
  }

  select(u: UserSummary): void {
    this.value.set(u);
    this.text.set(u);
  }
}
