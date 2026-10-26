import { Component, inject, signal } from '@angular/core';
import { FormsModule, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSelectModule } from '@angular/material/select';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api';
import { NotifyService } from '../../core/notify';
import { UserContextService } from '../../core/context';
import { Page } from '../../core/util';
import { confirm, SecretDialog } from '../../shared/confirm';
import { Group, OrgUnit, Ref, User } from './models';

@Component({
  selector: 'app-user-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatSlideToggleModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Felhasználó módosítása' : 'Új felhasználó' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-col">
        <mat-form-field><mat-label>Felhasználónév</mat-label><input matInput formControlName="username" /></mat-form-field>
        <mat-form-field><mat-label>Teljes név</mat-label><input matInput formControlName="fullName" /></mat-form-field>
        <mat-form-field><mat-label>E-mail</mat-label><input matInput formControlName="email" type="email" /></mat-form-field>
        @if (data) { <mat-slide-toggle formControlName="active">Aktív</mat-slide-toggle> }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Mégse</button>
      <button mat-flat-button [disabled]="form.invalid || busy()" (click)="save()">Mentés</button>
    </mat-dialog-actions>
  `,
})
export class UserDialog {
  readonly data = inject<User | null>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<UserDialog>);
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  readonly busy = signal(false);
  readonly form = inject(FormBuilder).nonNullable.group({
    username: [{ value: this.data?.username ?? '', disabled: !!this.data }, [Validators.required, Validators.pattern(/^[a-zA-Z0-9._@-]+$/)]],
    fullName: [this.data?.fullName ?? '', Validators.required],
    email: [this.data?.email ?? '', Validators.email],
    active: [this.data?.active ?? true],
  });

  save(): void {
    this.busy.set(true);
    const v = this.form.getRawValue();
    if (this.data) {
      this.api.put<User>(`iam/users/${this.data.id}`, { email: v.email || null, fullName: v.fullName, active: v.active, version: this.data.version })
        .subscribe({ next: (u) => this.ref.close(u), error: () => this.busy.set(false) });
    } else {
      this.api.post<{ user: User; temporaryPassword: string }>('iam/users', { username: v.username, fullName: v.fullName, email: v.email || null })
        .subscribe({
          next: (r) => {
            this.dialog.open(SecretDialog, { data: { title: 'Felhasználó létrehozva', message: `Ideiglenes jelszó (${r.user.username}), első belépéskor meg kell változtatni:`, secret: r.temporaryPassword } });
            this.ref.close(r.user);
          },
          error: () => this.busy.set(false),
        });
    }
  }
}

/** Felhasználó csoport- és szervezeti egység tagságai, hozzárendelés és törlés. */
@Component({
  selector: 'app-memberships-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatChipsModule, MatSelectModule, MatFormFieldModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ data.fullName }} – tagságok</h2>
    <mat-dialog-content>
      <h4>Csoportok</h4>
      <mat-chip-set>
        @for (g of groups(); track g.id) {
          <mat-chip [removable]="canWrite" (removed)="removeGroup(g)">{{ g.name }}
            @if (canWrite) { <button matChipRemove><mat-icon>cancel</mat-icon></button> }
          </mat-chip>
        } @empty { <span class="muted">Nincs csoporttagság</span> }
      </mat-chip-set>
      @if (canWrite) {
        <mat-form-field class="full"><mat-label>Csoporthoz rendelés</mat-label>
          <mat-select [(ngModel)]="groupToAdd" (selectionChange)="addGroup()">
            @for (g of allGroups(); track g.id) { <mat-option [value]="g.id">{{ g.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
      }
      <h4>Szervezeti egységek</h4>
      <mat-chip-set>
        @for (o of orgUnits(); track o.id) {
          <mat-chip [removable]="canWrite" (removed)="removeOrgUnit(o)">{{ o.name }}
            @if (canWrite) { <button matChipRemove><mat-icon>cancel</mat-icon></button> }
          </mat-chip>
        } @empty { <span class="muted">Nincs szervezeti egység</span> }
      </mat-chip-set>
      @if (canWrite) {
        <mat-form-field class="full"><mat-label>Szervezeti egységhez rendelés</mat-label>
          <mat-select [(ngModel)]="orgUnitToAdd" (selectionChange)="addOrgUnit()">
            @for (o of allOrgUnits(); track o.id) { <mat-option [value]="o.id">{{ o.name }} ({{ o.code }})</mat-option> }
          </mat-select>
        </mat-form-field>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button mat-flat-button mat-dialog-close>Kész</button></mat-dialog-actions>
  `,
})
export class MembershipsDialog {
  readonly data = inject<User>(MAT_DIALOG_DATA);
  private readonly api = inject(ApiService);
  readonly canWrite = inject(UserContextService).has('iam.write');
  readonly groups = signal<Ref[]>([]);
  readonly orgUnits = signal<Ref[]>([]);
  readonly allGroups = signal<Group[]>([]);
  readonly allOrgUnits = signal<OrgUnit[]>([]);
  groupToAdd: string | null = null;
  orgUnitToAdd: string | null = null;

  constructor() {
    this.reload();
    this.api.get<Group[]>('iam/groups').subscribe((g) => this.allGroups.set(g));
    this.api.get<OrgUnit[]>('iam/org-units').subscribe((o) => this.allOrgUnits.set(o));
  }

  reload(): void {
    this.api.get<{ groups: Ref[]; orgUnits: Ref[] }>(`iam/users/${this.data.id}/memberships`).subscribe((m) => {
      this.groups.set(m.groups);
      this.orgUnits.set(m.orgUnits);
    });
  }

  addGroup(): void {
    if (this.groupToAdd) {
      this.api.put(`iam/groups/${this.groupToAdd}/members/${this.data.id}`).subscribe(() => { this.groupToAdd = null; this.reload(); });
    }
  }

  removeGroup(g: Ref): void {
    this.api.delete(`iam/groups/${g.id}/members/${this.data.id}`).subscribe(() => this.reload());
  }

  addOrgUnit(): void {
    if (this.orgUnitToAdd) {
      this.api.put(`iam/org-units/${this.orgUnitToAdd}/members/${this.data.id}`).subscribe(() => { this.orgUnitToAdd = null; this.reload(); });
    }
  }

  removeOrgUnit(o: Ref): void {
    this.api.delete(`iam/org-units/${o.id}/members/${this.data.id}`).subscribe(() => this.reload());
  }
}

@Component({
  selector: 'app-users',
  imports: [FormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatTooltipModule],
  template: `
    <div class="page-header">
      <h1>Felhasználók</h1>
      @if (canWrite) { <button mat-flat-button (click)="edit(null)"><mat-icon>person_add</mat-icon> Új felhasználó</button> }
    </div>
    <div class="filters">
      <mat-form-field><mat-label>Keresés</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="load(0)" /></mat-form-field>
      <mat-form-field><mat-label>Állapot</mat-label>
        <mat-select [(ngModel)]="active" (selectionChange)="load(0)">
          <mat-option [value]="null">Mind</mat-option><mat-option [value]="true">Aktív</mat-option><mat-option [value]="false">Inaktív</mat-option>
        </mat-select>
      </mat-form-field>
      <button mat-stroked-button (click)="load(0)"><mat-icon>search</mat-icon> Keresés</button>
    </div>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="fullName"><th mat-header-cell *matHeaderCellDef>Név</th><td mat-cell *matCellDef="let u">{{ u.fullName }}</td></ng-container>
      <ng-container matColumnDef="username"><th mat-header-cell *matHeaderCellDef>Felhasználónév</th><td mat-cell *matCellDef="let u">{{ u.username }}</td></ng-container>
      <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>E-mail</th><td mat-cell *matCellDef="let u">{{ u.email }}</td></ng-container>
      <ng-container matColumnDef="type"><th mat-header-cell *matHeaderCellDef>Típus</th><td mat-cell *matCellDef="let u">{{ u.type === 'TECHNICAL' ? 'API kliens' : 'Személy' }}</td></ng-container>
      <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Aktív</th><td mat-cell *matCellDef="let u">{{ u.active ? 'igen' : 'nem' }}</td></ng-container>
      <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let u" class="actions">
          <button mat-icon-button matTooltip="Tagságok" (click)="memberships(u)"><mat-icon>groups</mat-icon></button>
          @if (canWrite && u.type === 'HUMAN') {
            <button mat-icon-button matTooltip="Módosítás" (click)="edit(u)"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button matTooltip="Új ideiglenes jelszó" (click)="resetPassword(u)"><mat-icon>key</mat-icon></button>
            @if (u.active) { <button mat-icon-button matTooltip="Törlés (inaktiválás)" (click)="remove(u)"><mat-icon>delete</mat-icon></button> }
          }
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
    <mat-paginator [length]="total()" [pageSize]="size" [pageIndex]="page()" [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" />
  `,
})
export class Users {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  readonly canWrite = inject(UserContextService).has('iam.write');
  readonly cols = ['fullName', 'username', 'email', 'type', 'active', 'actions'];
  readonly rows = signal<User[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  size = 50;
  q = '';
  active: boolean | null = true;

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.api.get<Page<User>>('iam/users', { q: this.q, active: this.active, page, size: this.size }).subscribe((p) => {
      this.rows.set(p.items);
      this.total.set(p.total);
      this.page.set(p.page);
    });
  }

  onPage(e: PageEvent): void {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }

  edit(u: User | null): void {
    this.dialog.open(UserDialog, { data: u, width: '480px' }).afterClosed().subscribe((r) => r && this.load(this.page()));
  }

  memberships(u: User): void {
    this.dialog.open(MembershipsDialog, { data: u, width: '560px' });
  }

  resetPassword(u: User): void {
    this.api.post<{ temporaryPassword: string }>(`iam/users/${u.id}/reset-password`).subscribe((r) =>
      this.dialog.open(SecretDialog, { data: { title: 'Új ideiglenes jelszó', message: u.username, secret: r.temporaryPassword } }));
  }

  remove(u: User): void {
    confirm(this.dialog, { title: 'Felhasználó törlése', message: `${u.fullName} inaktiválása. A bejelentkezése letiltódik, az adatai megmaradnak.`, ok: 'Törlés' })
      .subscribe((ok) => ok && this.api.delete(`iam/users/${u.id}`).subscribe(() => { this.notify.ok('Felhasználó inaktiválva'); this.load(this.page()); }));
  }
}
