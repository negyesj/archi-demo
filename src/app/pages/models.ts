export type TenantStatus = 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED' | 'FAILED';

export interface Tenant {
  code: string;
  name: string;
  status: TenantStatus;
  settings: Record<string, string>;
  enabledServices: string[];
  adminUsername: string;
  adminEmail: string;
  adminName: string;
  adminKeycloakId: string | null;
  lastError: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogEntry {
  id: string;
  name: string;
  version: string;
  core: boolean;
  manifest: { dependsOn?: string[]; permissions?: { code: string; name: string }[] };
}

export const STATUS_NEV: Record<TenantStatus, string> = {
  PROVISIONING: 'Létrehozás folyamatban',
  ACTIVE: 'Aktív',
  SUSPENDED: 'Felfüggesztve',
  ARCHIVED: 'Archivált',
  FAILED: 'Hibás',
};
