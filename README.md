# frontend/admin – Tenant admin felület (Angular + Angular Material)

Az üzemeltetők felülete a `platform` Keycloak realmmel (`admin-ui` kliens). Backend: `service/tenant-admin`
(a felületi gatewayen át, `/api/admin/**`; csak `platform` realm tokennel érhető el).

| Oldal | Funkció |
| --- | --- |
| Tenantok | lista, új tenant (szolgáltatások, első ügyfél-admin), állapot (felfüggesztés, aktiválás, archiválás, provisioning újraindítás) |
| Tenant részletek | engedélyezett szolgáltatások (függőség-ellenőrzéssel), beállítások (pl. iktatószám minta), első admin ideiglenes jelszava (egyszer) |
| Szolgáltatás-katalógus | a szolgáltatások manifestjei: verzió, függőségek, jogosultsági elemek |
| Admin felhasználók | platform admin / support felhasználók |
| Admin audit | az admin műveletek visszakeresése |
| Naplózási szintek | központi, futás közben állítható loglevel szolgáltatásonként, opcionálisan tenantra szűkítve, lejárattal |

## Fejlesztés

```bash
npm ci
npm start   # http://localhost:4300, /api → localhost:8080 (gateway-ui)
```

Konténer: ugyanúgy, mint a `frontend/app` (`API_UPSTREAM`, `KEYCLOAK_PUBLIC_URL`, `APP_NAME`).
