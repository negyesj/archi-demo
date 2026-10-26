import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface EffectivePermissions {
  userId: string | null;
  subject: string;
  displayName: string | null;
  permissions: string[];
  orgUnits: string[];
  groups: string[];
}

/** A bejelentkezett felhasználó effektív jogosultságai az IAM-ből (a menü és a gombok ehhez igazodnak). */
@Injectable({ providedIn: 'root' })
export class UserContextService {
  private readonly http = inject(HttpClient);
  readonly context = signal<EffectivePermissions | null>(null);
  readonly permissions = computed(() => new Set(this.context()?.permissions ?? []));

  async load(tenant: string): Promise<void> {
    const ctx = await firstValueFrom(this.http.get<EffectivePermissions>(`/api/t/${tenant}/iam/me/context`));
    this.context.set(ctx);
  }

  has(...codes: string[]): boolean {
    const p = this.permissions();
    return codes.some((c) => p.has(c));
  }
}
