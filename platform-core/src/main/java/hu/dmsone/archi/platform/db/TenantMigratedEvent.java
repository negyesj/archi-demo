package hu.dmsone.archi.platform.db;

import hu.dmsone.archi.platform.tenant.TenantInfo;

/** A szolgáltatás sémái naprakészek a tenant adatbázisában. */
public record TenantMigratedEvent(TenantInfo tenant) { }
