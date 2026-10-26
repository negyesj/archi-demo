import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../core/api';
import { UserContextService } from '../../core/context';
import { UserPicker, UserSummary } from '../../shared/pickers';

/** Csoport vagy szervezeti egység tagjai: felhasználó hozzárendelése és az összerendelés törlése. */
@Component({
  selector: 'app-members-dialog',
  imports: [MatDialogModule, MatButtonModule, MatListModule, MatIconModule, UserPicker],
  template: `
    <h2 mat-dialog-title>{{ data.title }} – tagok</h2>
    <mat-dialog-content>
      @if (canWrite) {
        <div class="row">
          <app-user-picker label="Felhasználó hozzáadása" [(value)]="selected" />
          <button mat-flat-button [disabled]="!selected()" (click)="add()">Hozzáadás</button>
        </div>
      }
      <mat-list>
        @for (m of members(); track m.id) {
          <mat-list-item>
            <span matListItemTitle>{{ m.fullName }}</span>
            <span matListItemLine>{{ m.username }}{{ m.active ? '' : ' (inaktív)' }}</span>
            @if (canWrite) {
              <button mat-icon-button matListItemMeta (click)="remove(m)"><mat-icon>person_remove</mat-icon></button>
            }
          </mat-list-item>
        } @empty { <p class="muted">Nincs tag.</p> }
      </mat-list>
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button mat-flat-button mat-dialog-close>Kész</button></mat-dialog-actions>
  `,
})
export class MembersDialog {
  readonly data = inject<{ title: string; basePath: string }>(MAT_DIALOG_DATA);
  private readonly api = inject(ApiService);
  readonly canWrite = inject(UserContextService).has('iam.write');
  readonly members = signal<UserSummary[]>([]);
  readonly selected = signal<UserSummary | null>(null);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.api.get<UserSummary[]>(`${this.data.basePath}/members`).subscribe((m) => this.members.set(m));
  }

  add(): void {
    const u = this.selected();
    if (u) {
      this.api.put(`${this.data.basePath}/members/${u.id}`).subscribe(() => this.reload());
    }
  }

  remove(u: UserSummary): void {
    this.api.delete(`${this.data.basePath}/members/${u.id}`).subscribe(() => this.reload());
  }
}
