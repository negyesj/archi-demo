# service/iam – Felhasználó- és jogosultságkezelés

Tenantonkénti alapszolgáltatás (minden tenantnál elérhető). A Keycloak csak hitelesít; a felhasználók törzsadata,
csoportjai, szervezeti egységei és **minden jogosultság** itt, a tenant adatbázisának `iam` sémájában van.
Nem használja sem a felhő IAM-et, sem a Keycloak szerepköreit.

| Terület | API (`/api/t/{tenant}/iam/...`) | Jogosultság |
| --- | --- | --- |
| Saját kontextus | `GET /me/context` (effektív jogok, szervezeti egységek, csoportok) | bejelentkezés |
| Felhasználók | `GET/POST /users`, `GET/PUT/DELETE /users/{id}`, `POST /users/{id}/reset-password`, `GET /users/{id}/memberships` | `iam.read`, `iam.write` |
| Választó más szolgáltatásoknak | `GET /users/lookup?q=`, `GET /users/{id}/summary` | bejelentkezés |
| Csoportok | `GET/POST /groups`, `GET/PUT/DELETE /groups/{id}`, `GET /groups/{id}/members`, `PUT/DELETE /groups/{id}/members/{userId}` | `iam.read`, `iam.write` |
| Szervezeti egységek | `GET/POST /org-units`, `GET/PUT/DELETE /org-units/{id}`, `.../members/{userId}` | `iam.read`, `iam.write` |
| Jogosultságok | `GET /permissions`, `GET/POST /grants`, `DELETE /grants/{id}` | `iam.permission.manage` |
| API kliensek | `GET/POST /api-clients`, `POST /api-clients/{id}/rotate-secret`, `DELETE /api-clients/{id}` | `integration.manage` |
| Publikus API v1 | `/public/v1/t/{tenant}/iam/users|groups|org-units` | `iam.read` |

## Működés

- A jogosultsági elemeket a szolgáltatások manifestjeiből (`platform.catalog`) szinkronizálja a tenant adatbázisába;
  csak a tenantnak engedélyezett szolgáltatások elemei aktívak.
- A „Rendszeradminisztrátorok” csoport minden aktív elemet megkap; az első ügyfél-admin ide kerül.
- Minden változás az outboxon át az `iam.events` topicra megy (audit, riport), a jogosultságot érintő változások
  után a tenant Redis jogosultság-cache-e törlődik, így egy jogelvétel azonnal hat.
- A felhasználó törlése inaktiválás (az audit és az iratok hivatkoznak rá); a Keycloak fiók letiltódik.
