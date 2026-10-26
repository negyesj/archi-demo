import { Injectable } from '@angular/core';

/** Futásidejű konfiguráció (/config.json): a konténer indulásakor környezeti változókból generálódik. */
export interface AppConfig {
  keycloakUrl: string;
  appName: string;
}

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private cfg: AppConfig = { keycloakUrl: 'http://localhost:8180', appName: 'Üzleti platform' };

  async load(): Promise<void> {
    try {
      const res = await fetch('/config.json', { cache: 'no-store' });
      if (res.ok) {
        this.cfg = { ...this.cfg, ...(await res.json()) };
      }
    } catch {
      // alapértékekkel indulunk
    }
  }

  get value(): AppConfig {
    return this.cfg;
  }
}
