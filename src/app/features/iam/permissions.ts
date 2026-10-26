import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../../core/api';
import { NotifyService } from '../../core/notify';
import { UserPicker, UserSummary } from '../../shared/pickers';
import { Grant, Group, Permission } from './models';

/**
 * Jogosultságkezelés: a tenanthoz rendelt szolgáltatások jogosultsági elemeinek hozzárendelése felhasználóhoz
 * vagy csoporthoz, és a hozzárendelés törlése.
 */
@Component({
  selector: 'app-permissions',
  imports: [FormsModule, MatButtonToggleModule, MatFormFieldModule, MatSelectModule, MatButtonModule, MatIconModule, MatTableModule, UserPicker],
  template: `
    <h1>Jogosultságok</h1>
    <div class="filters">
      <mat-button-toggle-group [(ngModel)]="subjectType" (change)="subjectChanged()">
        <mat-button-toggle value="USER">Felhasználó</mat-button-toggle>
        <mat-button-toggle value="GROUP">Csoport</mat-button-toggle>
      </mat-button-toggle-group>
      @if (subjectType === 'USER') {
        <app-user-picker label="Felhasználó" [(value)]="user" />
      } @else {
        <mat-form-field><mat-label>Csoport</mat-label>
          <mat-select [(ngModel)]="groupId" (selectionChange)="loadGrants()">
            @for (g of groups(); track g.id) { <mat-option [value]="g.id">{{ g.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
      }
    </div>
    @if (subjectId()) {
      <div class="filters">
        <mat-form-field class="wide"><mat-label>Jogosultsági elem hozzárendelése</mat-label>
          <mat-select [(ngModel)]="toGrant">
            @for (p of available(); track p.code) { <mat-option [value]="p.code">{{ p.name }} <small class="muted">({{ p.code }})</small></mat-option> }
          </mat-select>
        </mat-form-field>
        <button mat-flat-button [disabled]="!toGrant" (click)="grant()"><mat-icon>add</mat-icon> Hozzárendelés</button>
      </div>
      <table mat-table [dataSource]="grants()" class="full">
        <ng-container matColumnDef="service"><th mat-header-cell *matHeaderCellDef>Szolgáltatás</th><td mat-cell *matCellDef="let g">{{ serviceOf(g.permissionCode) }}</td></ng-container>
        <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Jogosultság</th><td mat-cell *matCellDef="let g">{{ g.permissionName }} <small class="muted">({{ g.permissionCode }})</small></td></ng-container>
        <ng-container matColumnDef="by"><th mat-header-cell *matHeaderCellDef>Kiosztotta</th><td mat-cell *matCellDef="let g">{{ g.createdBy }}</td></ng-container>
        <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let g" class="actions"><button mat-icon-button (click)="revoke(g)"><mat-icon>delete</mat-icon></button></td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let row; columns: cols"></tr>
      </table>
      @if (subjectType === 'USER') {
        <p class="muted">A felhasználó a csoportjain keresztül további jogosultságokat is kaphat (lásd Csoportok).</p>
      }
    }
  `,
})
export class Permissions {
  private readonly api = inject(ApiService);
  private readonly notify = inject(NotifyService);
  readonly cols = ['service', 'name', 'by', 'actions'];
  readonly permissions = signal<Permission[]>([]);
  readonly groups = signal<Group[]>([]);
  readonly grants = signal<Grant[]>([]);
  readonly user = signal<UserSummary | null>(null);
  readonly group = signal<string | null>(null);
  private readonly st = signal<'USER' | 'GROUP'>('USER');
  toGrant: string | null = null;

  get subjectType(): 'USER' | 'GROUP' { return this.st(); }
  set subjectType(v: 'USER' | 'GROUP') { this.st.set(v); }
  get groupId(): string | null { return this.group(); }
  set groupId(v: string | null) { this.group.set(v); }

  readonly subjectId = computed(() => (this.st() === 'USER' ? this.user()?.id : this.group()) ?? null);
  readonly available = computed(() => {
    const granted = new Set(this.grants().map((g) => g.permissionCode));
    return this.permissions().filter((p) => !granted.has(p.code));
  });

  constructor() {
    this.api.get<Permission[]>('iam/permissions').subscribe((p) => this.permissions.set(p));
    this.api.get<Group[]>('iam/groups').subscribe((g) => this.groups.set(g));
    effect(() => {
      this.user();
      this.loadGrants();
    });
  }

  serviceOf(code: string): string {
    return this.permissions().find((p) => p.code === code)?.serviceId ?? '';
  }

  subjectChanged(): void {
    this.grants.set([]);
    this.loadGrants();
  }

  loadGrants(): void {
    const id = this.subjectId();
    if (!id) {
      this.grants.set([]);
      return;
    }
    this.api.get<Grant[]>('iam/grants', { subjectType: this.subjectType, subjectId: id }).subscribe((g) => this.grants.set(g));
  }

  grant(): void {
    const id = this.subjectId();
    if (!id || !this.toGrant) {
      return;
    }
    this.api.post<Grant>('iam/grants', { permissionCode: this.toGrant, subjectType: this.subjectType, subjectId: id })
      .subscribe(() => { this.toGrant = null; this.notify.ok('Jogosultság hozzárendelve'); this.loadGrants(); });
  }

  revoke(g: Grant): void {
    this.api.delete(`iam/grants/${g.id}`).subscribe(() => { this.notify.ok('Hozzárendelés törölve'); this.loadGrants(); });
  }
}
