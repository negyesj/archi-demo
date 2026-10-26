import { Injectable, signal } from '@angular/core';

export interface TenantInfo {
  code: string;
  name: string;
  services: string[];
}

const RECENT_KEY = 'platform.recentTenants';

/** Az aktuális tenant (az URL /t/{tenant} szegmenséből) és a böngészőben megjegyzett, korábban használt tenantok. */
@Injectable({ providedIn: 'root' })
export class TenantService {
  readonly current = signal<TenantInfo | null>(null);

  /** Nyilvános tenant információ; nem létező vagy inaktív kódra null (semleges hibaüzenethez). */
  async lookup(code: string): Promise<TenantInfo | null> {
    if (!/^[a-z][a-z0-9]{1,29}$/.test(code)) {
      return null;
    }
    const res = await fetch(`/api/tenants/${encodeURIComponent(code)}`);
    return res.ok ? ((await res.json()) as TenantInfo) : null;
  }

  async activate(code: string): Promise<TenantInfo | null> {
    if (this.current()?.code === code) {
      return this.current();
    }
    const info = await this.lookup(code);
    this.current.set(info);
    if (info) {
      this.remember(info);
    }
    return info;
  }

  code(): string {
    const t = this.current();
    if (!t) {
      throw new Error('Nincs kiválasztott tenant');
    }
    return t.code;
  }

  hasService(id: string): boolean {
    return this.current()?.services.includes(id) ?? false;
  }

  recent(): TenantInfo[] {
    try {
      return JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]') as TenantInfo[];
    } catch {
      return [];
    }
  }

  private remember(info: TenantInfo): void {
    const list = [info, ...this.recent().filter((t) => t.code !== info.code)].slice(0, 5);
    try {
      localStorage.setItem(RECENT_KEY, JSON.stringify(list.map((t) => ({ code: t.code, name: t.name, services: [] }))));
    } catch {
      // privát mód: nem baj
    }
  }
}
