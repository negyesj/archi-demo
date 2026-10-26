# service/tenant-admin – Tenant admin szolgáltatás (control plane)

Az admin felület (`frontend/admin`) backendje. Csak a `platform` Keycloak realm aktív admin felhasználói érik el
(`PLATFORM_ADMIN`: minden, `SUPPORT`: olvasás és loglevel). Ügyféladathoz nem fér hozzá.

| Funkció | API (`/api/admin/...`) |
| --- | --- |
| Tenant kezelés | `GET/POST /tenants`, `GET/PUT /tenants/{kod}`, `POST /tenants/{kod}/suspend|activate|archive|provision` |
| Tenant konfiguráció | `PUT /tenants/{kod}/services` – függőség-ellenőrzés a katalógus alapján |
| Első ügyfél-admin jelszava | `POST /tenants/{kod}/bootstrap-password` – egyszer olvasható ki |
| Szolgáltatás-katalógus | `GET /catalog` (a szolgáltatások `platform.catalog` manifestjeiből) |
| Admin felhasználók | `GET /me`, `GET/POST /admin-users`, `PUT /admin-users/{id}` |
| Admin audit | `GET /audit?from&to&actor&action&targetType&targetId` |
| Loglevel | `GET/PUT/DELETE /loglevels` (szolgáltatás, logger, szint, opcionális tenant, lejárat) |

## Folyamat: tenant létrehozás

1. `POST /tenants` → `PROVISIONING`, parancs a `platform.tenant.commands` topicra (tranzakció után).
2. A `service/provisioner` létrehozza a PostgreSQL és MongoDB adatbázist, a Keycloak realmet és az első admint.
3. `platform.tenant.events` `PROVISIONED` → `ACTIVE`, az állapot a compacted `platform.tenants` topicra kerül.
4. Minden szolgáltatás a saját sémáit migrálja a tenant adatbázisában, az IAM létrehozza az első admin felhasználót.

Minden admin művelet aszinkron módon (`platform.audit`) az admin audit táblába kerül.
