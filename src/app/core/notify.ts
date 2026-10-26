import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snack = inject(MatSnackBar);

  ok(message: string): void {
    this.snack.open(message, 'OK', { duration: 3500 });
  }

  error(message: string): void {
    this.snack.open(message, 'Bezár', { duration: 10000, panelClass: 'snack-error' });
  }
}
