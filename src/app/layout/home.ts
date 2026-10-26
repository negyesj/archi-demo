import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TenantService } from '../core/tenant';
import { UserContextService } from '../core/context';
import { MENU } from './menu';

@Component({
  selector: 'app-home',
  imports: [RouterLink, MatCardModule, MatIconModule],
  template: `
    <h1>Üdvözöljük{{ context.context()?.displayName ? ', ' + context.context()?.displayName : '' }}!</h1>
    @if (tiles().length === 0) {
      <p class="muted">Még nincs jogosultsága egyetlen funkcióhoz sem. Kérje a szervezete adminisztrátorát.</p>
    }
    <div class="tiles">
      @for (t of tiles(); track t.path) {
        <mat-card class="tile" [routerLink]="'/t/' + tenant.code() + '/' + t.path">
          <mat-card-content><mat-icon>{{ t.icon }}</mat-icon><div>{{ t.label }}</div></mat-card-content>
        </mat-card>
      }
    </div>
  `,
})
export class Home {
  readonly tenant = inject(TenantService);
  readonly context = inject(UserContextService);
  readonly tiles = computed(() => {
    const perms = this.context.permissions();
    const services = this.tenant.current()?.services ?? [];
    return MENU.flatMap((g) => g.items).filter((i) => services.includes(i.service) && i.permissions.some((p) => perms.has(p)));
  });
}
