import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { ApiService } from '../../core/api';
import { NotifyService } from '../../core/notify';
import { UserContextService } from '../../core/context';
import { Page } from '../../core/util';
import { confirm } from '../../shared/confirm';

export interface Partner {
  id: string; name: string; taxNumber: string | null; registrationNumber: string | null; country: string;
  postalCode: string | null; city: string | null; address: string | null; email: string | null; phone: string | null;
  active: boolean; version: number;
}

@Component({
  selector: 'app-partner-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Partner módosítása' : 'Új partner' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="form-grid">
        <mat-form-field class="span2"><mat-label>Név</mat-label><input matInput formControlName="name" /></mat-form-field>
        <mat-form-field><mat-label>Adószám</mat-label><input matInput formControlName="taxNumber" /></mat-form-field>
        <mat-form-field><mat-label>Cégjegyzékszám</mat-label><input matInput formControlName="registrationNumber" /></mat-form-field>
        <mat-form-field><mat-label>Ország</mat-label><input matInput formControlName="country" maxlength="2" /></mat-form-field>
        <mat-form-field><mat-label>Irányítószám</mat-label><input matInput formControlName="postalCode" /></mat-form-field>
        <mat-form-field><mat-label>Település</mat-label><input matInput formControlName="city" /></mat-form-field>
        <mat-form-field><mat-label>Cím</mat-label><input matInput formControlName="address" /></mat-form-field>
        <mat-form-field><mat-label>E-mail</mat-label><input matInput formControlName="email" /></mat-form-field>
        <mat-form-field><mat-label>Telefon</mat-label><input matInput formControlName="phone" /></mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Mégse</button>
      <button mat-flat-button [disabled]="form.invalid" (click)="save()">Mentés</button>
    </mat-dialog-actions>
  `,
})
export class PartnerDialog {
  readonly data = inject<Partner | null>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<PartnerDialog>);
  private readonly api = inject(ApiService);
  readonly form = inject(FormBuilder).group({
    name: [this.data?.name ?? '', [Validators.required, Validators.maxLength(300)]],
    taxNumber: [this.data?.taxNumber ?? '', Validators.pattern(/^$|^[0-9-]{8,30}$/)],
    registrationNumber: [this.data?.registrationNumber ?? ''],
    country: [this.data?.country ?? 'HU', [Validators.required, Validators.minLength(2)]],
    postalCode: [this.data?.postalCode ?? ''],
    city: [this.data?.city ?? ''],
    address: [this.data?.address ?? ''],
    email: [this.data?.email ?? '', Validators.email],
    phone: [this.data?.phone ?? ''],
  });

  save(): void {
    const v = this.form.getRawValue();
    const body = Object.fromEntries(Object.entries(v).map(([k, x]) => [k, x === '' ? null : x]));
    const req = this.data
      ? this.api.put<Partner>(`partner/partners/${this.data.id}`, { ...body, version: this.data.version })
      : this.api.post<Partner>('partner/partners', body);
    req.subscribe((p) => this.ref.close(p));
  }
}

@Component({
  selector: 'app-partners',
  imports: [FormsModule, MatTableModule, MatPaginatorModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <div class="page-header">
      <h1>Partnerek</h1>
      @if (canWrite) { <button mat-flat-button (click)="edit(null)"><mat-icon>add_business</mat-icon> Új partner</button> }
    </div>
    <div class="filters">
      <mat-form-field class="wide"><mat-label>Keresés (név, adószám, település)</mat-label><input matInput [(ngModel)]="q" (keyup.enter)="load(0)" /></mat-form-field>
      <button mat-stroked-button (click)="load(0)"><mat-icon>search</mat-icon> Keresés</button>
    </div>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Név</th><td mat-cell *matCellDef="let p">{{ p.name }}</td></ng-container>
      <ng-container matColumnDef="taxNumber"><th mat-header-cell *matHeaderCellDef>Adószám</th><td mat-cell *matCellDef="let p">{{ p.taxNumber }}</td></ng-container>
      <ng-container matColumnDef="address"><th mat-header-cell *matHeaderCellDef>Cím</th><td mat-cell *matCellDef="let p">{{ p.postalCode }} {{ p.city }} {{ p.address }}</td></ng-container>
      <ng-container matColumnDef="active"><th mat-header-cell *matHeaderCellDef>Aktív</th><td mat-cell *matCellDef="let p">{{ p.active ? 'igen' : 'törölt' }}</td></ng-container>
      <ng-container matColumnDef="actions"><th mat-header-cell *matHeaderCellDef></th>
        <td mat-cell *matCellDef="let p" class="actions">
          @if (canWrite && p.active) {
            <button mat-icon-button matTooltip="Módosítás" (click)="edit(p)"><mat-icon>edit</mat-icon></button>
            <button mat-icon-button matTooltip="Törlés" (click)="remove(p)"><mat-icon>delete</mat-icon></button>
          }
        </td></ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
    <mat-paginator [length]="total()" [pageSize]="size" [pageIndex]="page()" [pageSizeOptions]="[25, 50, 100]" (page)="onPage($event)" />
  `,
})
export class Partners {
  private readonly api = inject(ApiService);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  readonly canWrite = inject(UserContextService).has('partner.write');
  readonly cols = ['name', 'taxNumber', 'address', 'active', 'actions'];
  readonly rows = signal<Partner[]>([]);
  readonly total = signal(0);
  readonly page = signal(0);
  size = 50;
  q = '';

  constructor() {
    this.load(0);
  }

  load(page: number): void {
    this.api.get<Page<Partner>>('partner/partners', { q: this.q, page, size: this.size }).subscribe((p) => {
      this.rows.set(p.items);
      this.total.set(p.total);
      this.page.set(p.page);
    });
  }

  onPage(e: PageEvent): void {
    this.size = e.pageSize;
    this.load(e.pageIndex);
  }

  edit(p: Partner | null): void {
    this.dialog.open(PartnerDialog, { data: p, width: '640px' }).afterClosed().subscribe((r) => {
      if (r) {
        this.notify.ok('Partner mentve');
        this.load(this.page());
      }
    });
  }

  remove(p: Partner): void {
    confirm(this.dialog, { title: 'Partner törlése', message: `${p.name} törlése (logikai: az iratok hivatkozása megmarad).`, ok: 'Törlés' })
      .subscribe((ok) => ok && this.api.delete('partner/partners/' + p.id, { version: p.version })
        .subscribe(() => { this.notify.ok('Partner törölve'); this.load(this.page()); }));
  }
}
