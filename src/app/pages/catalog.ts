import { Component, inject, signal } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { AdminApi } from '../core/admin-api';
import { CatalogEntry } from './models';

/** A szolgáltatások által induláskor publikált manifestek: verzió, függőségek, jogosultsági elemek. */
@Component({
  selector: 'app-catalog',
  imports: [MatTableModule],
  template: `
    <h1>Szolgáltatás-katalógus</h1>
    <table mat-table [dataSource]="rows()" class="full">
      <ng-container matColumnDef="id"><th mat-header-cell *matHeaderCellDef>Azonosító</th><td mat-cell *matCellDef="let s"><code>{{ s.id }}</code></td></ng-container>
      <ng-container matColumnDef="name"><th mat-header-cell *matHeaderCellDef>Név</th><td mat-cell *matCellDef="let s">{{ s.name }}{{ s.core ? ' (alap)' : '' }}</td></ng-container>
      <ng-container matColumnDef="version"><th mat-header-cell *matHeaderCellDef>Verzió</th><td mat-cell *matCellDef="let s">{{ s.version }}</td></ng-container>
      <ng-container matColumnDef="deps"><th mat-header-cell *matHeaderCellDef>Függőségek</th><td mat-cell *matCellDef="let s">{{ (s.manifest.dependsOn ?? []).join(', ') }}</td></ng-container>
      <ng-container matColumnDef="perms"><th mat-header-cell *matHeaderCellDef>Jogosultsági elemek</th>
        <td mat-cell *matCellDef="let s">@for (p of s.manifest.permissions ?? []; track p.code) { <div><code>{{ p.code }}</code> – {{ p.name }}</div> }</td></ng-container>
      <tr mat-header-row *matHeaderRowDef="cols"></tr>
      <tr mat-row *matRowDef="let row; columns: cols"></tr>
    </table>
  `,
})
export class Catalog {
  readonly cols = ['id', 'name', 'version', 'deps', 'perms'];
  readonly rows = signal<CatalogEntry[]>([]);

  constructor() {
    inject(AdminApi).get<CatalogEntry[]>('catalog').subscribe((c) => this.rows.set(c));
  }
}
