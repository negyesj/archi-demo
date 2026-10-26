import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialog } from '@angular/material/dialog';
import { AdminApi, AdminUser } from '../core/admin-api';
import { NotifyService } from '../core/notify';
import { SecretDialog } from '../shared/confirm';

/** A tenant admin felületre belépő üzemeltetők (Keycloak platform realm). */
@Component({
  selector: 'app-admin-users',
  imports: [FormsModule, MatTableModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule],
  template: `
    <h1>Admin felhasználók</h1>
    @if (api.isAdmin()) {
      <div class="filters">
        <mat-form-field><mat-label>Felhasználónév</mat-label><input matInput [(ngModel)]="n.username" /></mat-form-field>
        <mat-form-field><mat-label>Név</mat-label><input matInput [(ngModel)]="n.fullName" /></mat-form-field>
        <mat-form-field><mat-label>E-mail</mat-label><input matInput [(ngModel)]="n.email" /></mat-form-field>
        <mat-form-field><mat-label>Szerepkör</mat-label>
          <mat-select [(ngModel)]="n.role"><mat-option value="PLATFORM_ADMIN">Platform admin</mat-option><mat-option value="SUPPORT">Support</mat-option></mat-select></mat-form-field>
        <button mat-flat-button [disabled]="!n.username || !n.fullName || !n.email" (click)="create()"><mat-icon>person_add</mat-icon> Létrehozás</button>
      </div>
    }
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="username"><th mat-header-cell *matHeaderCellDef>Felhasználónév</th><td mat-cell *matCellDef="let u">{{ u.username }}</td></ng-container>
      <ng-container matColumnDef="fullName"><th mat-header-cell *matHeaderCellDef>Név</th><td mat-cell *matCellDef="let u">{{ u.fullName }}</td></ng-container>
      <ng-container matColumnDef="email"><th mat-header-cell *matHeaderCellDef>E-mail</th><td mat-cell *matCellDef="let u">{{ u.email }}</td></ng-container>
      <ng-container matColumnDef="role"><th mat-header-cell *matHeaderCellDef>Szerepkör</th>
        <td mat-cell *matCellDef="let u">
          <mat-select [value]="u.role" [disabled]="!api.isAdmin()" (selectionChange)="update(u, $event.value, u.active)">
            <mat-option value="PLATFORM_ADMIN">Platform admin</mat-option><mat-option value="SUPPORT">Support</mat-option>
          </mat-select>
        </td></ng-container>
      <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Aktív</th>
        <td mat-cell *matCellDef="let u"><mat-slide-toggle [checked]="u.active" [disabled]="!api.isAdmin()" (change)="update(u, u.role, $event.checked)" /></td></ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
  `,
})
export class AdminUsers {
  readonly api = inject(AdminApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  readonly cols = ['username', 'fullName', 'email', 'role', 'active'];
  readonly rows = signal<AdminUser[]>([]);
  n = { username: '', fullName: '', email: '', role: 'SUPPORT' };

  constructor() {
    this.load();
  }

  load(): void {
    this.api.get<AdminUser[]>('admin-users').subscribe((u) => this.rows.set(u));
  }

  create(): void {
    this.api.post<{ user: AdminUser; temporaryPassword: string }>('admin-users', this.n).subscribe((r) => {
      this.dialog.open(SecretDialog, { data: { title: 'Admin felhasználó létrehozva', message: r.user.username, secret: r.temporaryPassword } });
      this.n = { username: '', fullName: '', email: '', role: 'SUPPORT' };
      this.load();
    });
  }

  update(u: AdminUser, role: string, active: boolean): void {
    this.api.put<AdminUser>(`admin-users/${u.id}`, { email: u.email, fullName: u.fullName, role, active })
      .subscribe(() => { this.notify.ok('Mentve'); this.load(); });
  }
}
