import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { Observable } from 'rxjs';

export interface ConfirmData {
  title: string;
  message: string;
  ok?: string;
}

@Component({
  selector: 'app-confirm',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>{{ data.message }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Mégse</button>
      <button mat-flat-button [mat-dialog-close]="true">{{ data.ok ?? 'OK' }}</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialog {
  readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
}

export function confirm(dialog: MatDialog, data: ConfirmData): Observable<boolean | undefined> {
  return dialog.open(ConfirmDialog, { data }).afterClosed();
}

/** Egyszer megjelenő titok (ideiglenes jelszó, kliens titok) megmutatása. */
@Component({
  selector: 'app-secret',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p>{{ data.message }}</p>
      <pre class="secret">{{ data.secret }}</pre>
      <p class="muted">Ez az érték csak most látható, utána nem kérhető le újra.</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button mat-dialog-close>Bezár</button>
    </mat-dialog-actions>
  `,
})
export class SecretDialog {
  readonly data = inject<{ title: string; message: string; secret: string }>(MAT_DIALOG_DATA);
}
