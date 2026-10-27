import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { ApiService } from '../../core/api';
import { UserContextService } from '../../core/context';
import { NotifyService } from '../../core/notify';
import { confirm } from '../../shared/confirm';
import { Group } from './models';
import { MembersDialog } from './members';

@Component({
  selector: 'app-group-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Csoport módosítása' : 'Új csoport' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-col">
        <div class="form-item"><label class="form-label">Név</label><mat-form-field><input matInput formControlName="name" /></mat-form-field></div>
        <div class="form-item"><label class="form-label">Leírás</label><mat-form-field><textarea matInput formControlName="description" rows="3"></textarea></mat-form-field></div>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Mégse</button>
      <button mat-flat-button [disabled]="form.invalid" (click)="save()">Mentés</button>
    </mat-dialog-actions>
  `,
})
export class GroupDialog {
  readonly data = inject<Group | null>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<GroupDialog>);
  private readonly api = inject(ApiService);
  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.data?.name ?? '', Validators.required],
    description: [this.data?.description ?? ''],
  });

  save(): void {
    const body = { ...this.form.getRawValue(), version: this.data?.version ?? null };
    const req = this.data ? this.api.put<Group>(`iam/groups/${this.data.id}`, body) : this.api.post<Group>('iam/groups', body);
    req.subscribe((g) => this.ref.close(g));
  }
}

@Component({
  selector: 'app-groups',
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <div class="page-header">
      <h1>Felhasználócsoportok</h1>
      @if (canWrite) { <button mat-flat-button (click)="edit(null)"><mat-icon>group_add</mat-icon> Új csoport</button> }
    </div>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Név</th>
        <td mat-cell *matCellDef="let g">{{ g.name }} @if (g.system) { <small class="muted">(rendszer)</small> }</td></ng-container>
      <ng-container matColumnDef="description"><th mat-header-cell *matHeaderCellDef>Leírás</th><td mat-cell *matCellDef="let g">{{ g.description }}</td></ng-container>
      <ng-container matColumnDef="members"><th mat-header-cell *matHeaderCellDef>Tagok</th><td mat-cell *matCellDef="let g">{{ g.memberCount }}</td></ng-container>
      <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let g" class="actions">
          <button mat-icon-button matTooltip="Tagok" (click)="members(g)"><mat-icon>groups</mat-icon></button>
          @if (canWrite) {
            <button mat-icon-button matTooltip="Módosítás" (click)="edit(g)"><mat-icon>edit</mat-icon></button>
            @if (!g.system) { <button mat-icon-button matTooltip="Törlés" (click)="remove(g)"><mat-icon>delete</mat-icon></button> }
          }
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
  `,
})
export class Groups {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  readonly canWrite = inject(UserContextService).has('iam.write');
  readonly cols = ['name', 'description', 'members', 'actions'];
  readonly rows = signal<Group[]>([]);

  constructor() {
    this.load();
  }

  load(): void {
    this.api.get<Group[]>('iam/groups').subscribe((g) => this.rows.set(g));
  }

  edit(g: Group | null): void {
    this.dialog.open(GroupDialog, { data: g, width: '480px' }).afterClosed().subscribe((r) => r && this.load());
  }

  members(g: Group): void {
    this.dialog.open(MembersDialog, { data: { title: g.name, basePath: `iam/groups/${g.id}` }, width: '560px' })
      .afterClosed().subscribe(() => this.load());
  }

  remove(g: Group): void {
    confirm(this.dialog, { title: 'Csoport törlése', message: `${g.name}: a tagságok és a csoport jogosultságai is törlődnek.`, ok: 'Törlés' })
      .subscribe((ok) => ok && this.api.delete(`iam/groups/${g.id}`).subscribe(() => { this.notify.ok('Csoport törölve'); this.load(); }));
  }
}
