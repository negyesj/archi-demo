import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { ApiService } from '../../core/api';
import { UserContextService } from '../../core/context';
import { NotifyService } from '../../core/notify';
import { confirm } from '../../shared/confirm';
import { OrgUnit } from './models';
import { MembersDialog } from './members';

@Component({
  selector: 'app-org-unit-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.unit ? 'Szervezeti egység módosítása' : 'Új szervezeti egység' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-col">
        <mat-form-field><mat-label>Kód</mat-label><input matInput formControlName="code" /></mat-form-field>
        <mat-form-field><mat-label>Név</mat-label><input matInput formControlName="name" /></mat-form-field>
        <mat-form-field><mat-label>Szülő egység</mat-label>
          <mat-select formControlName="parentId">
            <mat-option [value]="null">– (legfelső szint)</mat-option>
            @for (o of parents; track o.id) { <mat-option [value]="o.id">{{ o.name }}</mat-option> }
          </mat-select>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Mégse</button>
      <button mat-flat-button [disabled]="form.invalid" (click)="save()">Mentés</button>
    </mat-dialog-actions>
  `,
})
export class OrgUnitDialog {
  readonly data = inject<{ unit: OrgUnit | null; all: OrgUnit[] }>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<OrgUnitDialog>);
  private readonly api = inject(ApiService);
  readonly parents = this.data.all.filter((o) => o.id !== this.data.unit?.id);
  readonly form = inject(FormBuilder).group({
    code: [this.data.unit?.code ?? '', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]+$/)]],
    name: [this.data.unit?.name ?? '', Validators.required],
    parentId: [this.data.unit?.parentId ?? null as string | null],
  });

  save(): void {
    const body = { ...this.form.getRawValue(), version: this.data.unit?.version ?? null };
    const req = this.data.unit ? this.api.put<OrgUnit>(`iam/org-units/${this.data.unit.id}`, body) : this.api.post<OrgUnit>('iam/org-units', body);
    req.subscribe((o) => this.ref.close(o));
  }
}

interface Row extends OrgUnit {
  depth: number;
  path: string;
}

@Component({
  selector: 'app-org-units',
  imports: [MatTableModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <div class="page-header">
      <h1>Szervezeti egységek</h1>
      @if (canWrite) { <button mat-flat-button (click)="edit(null)"><mat-icon>add</mat-icon> Új egység</button> }
    </div>
    <table mat-table [dataSource]="tree()" class="full">
      <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Név</th>
        <td mat-cell *matCellDef="let o"><span [style.padding-left.px]="o.depth * 24">{{ o.name }}</span></td></ng-container>
      <ng-container matColumnDef="code"><th mat-header-cell *matHeaderCellDef>Kód</th><td mat-cell *matCellDef="let o">{{ o.code }}</td></ng-container>
      <ng-container matColumnDef="members"><th mat-header-cell *matHeaderCellDef>Tagok</th><td mat-cell *matCellDef="let o">{{ o.memberCount }}</td></ng-container>
      <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let o" class="actions">
          <button mat-icon-button matTooltip="Tagok" (click)="members(o)"><mat-icon>groups</mat-icon></button>
          @if (canWrite) {
            <button mat-icon-button matTooltip="Módosítás" (click)="edit(o)"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button matTooltip="Törlés" (click)="remove(o)"><mat-icon>delete</mat-icon></button>
          }
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
  `,
})
export class OrgUnits {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  readonly canWrite = inject(UserContextService).has('iam.write');
  readonly cols = ['name', 'code', 'members', 'actions'];
  readonly units = signal<OrgUnit[]>([]);

  /** Fa sorrend: szülő után a gyerekei, behúzással. */
  readonly tree = computed<Row[]>(() => {
    const all = this.units();
    const out: Row[] = [];
    const walk = (parent: string | null, depth: number, path: string) => {
      all.filter((o) => o.parentId === parent).sort((a, b) => a.name.localeCompare(b.name, 'hu'))
        .forEach((o) => {
          out.push({ ...o, depth, path: path + '/' + o.name });
          walk(o.id, depth + 1, path + '/' + o.name);
        });
    };
    walk(null, 0, '');
    return out;
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.api.get<OrgUnit[]>('iam/org-units').subscribe((o) => this.units.set(o));
  }

  edit(o: OrgUnit | null): void {
    this.dialog.open(OrgUnitDialog, { data: { unit: o, all: this.units() }, width: '480px' }).afterClosed().subscribe((r) => r && this.load());
  }

  members(o: OrgUnit): void {
    this.dialog.open(MembersDialog, { data: { title: o.name, basePath: `iam/org-units/${o.id}` }, width: '560px' })
      .afterClosed().subscribe(() => this.load());
  }

  remove(o: OrgUnit): void {
    confirm(this.dialog, { title: 'Szervezeti egység törlése', message: `${o.name} törlése. A tagságok is törlődnek.`, ok: 'Törlés' })
      .subscribe((ok) => ok && this.api.delete(`iam/org-units/${o.id}`).subscribe(() => { this.notify.ok('Törölve'); this.load(); }));
  }
}
