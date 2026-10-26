# frontend/app – Felhasználói felület (Angular + Angular Material)

A tenantok felhasználóinak felülete. A backendtől teljesen elkülönül: csak a felületi API Gatewayen keresztül,
REST/JSON-on kommunikál (`/api/t/{tenant}/{szolgáltatás}/...`). Zoneless Angular, signalok, lusta betöltésű
szolgáltatásonkénti felületek.

## URL szerkezet

| URL | Tartalom |
| --- | --- |
| `/` | Tenant választó (kód megadása vagy a korábban használt tenantok; az ügyfelek listája nem jelenik meg) |
| `/t/{tenant}` | A tenant login képernyője (Keycloak realm), bejelentkezve a kezdőlap |
| `/t/{tenant}/irat/erkeztetes`, `/irat/feldolgozas`, `/irat/kereso` | Bejövő irat kezelés |
| `/t/{tenant}/partner` | Partnerek |
| `/t/{tenant}/iam/users`, `/groups`, `/org-units`, `/permissions`, `/integrations` | Adminisztráció |
| `/t/{tenant}/audit` | Audit napló |

A menü a tenantnak engedélyezett szolgáltatásokból és a felhasználó IAM-jogosultságaiból (`/iam/me/context`) épül.
A jogosultság szerinti elrejtés csak kényelmi funkció; a tényleges ellenőrzés a backendben van.

## Fejlesztés

```bash
npm ci
npm start            # http://localhost:4200, a /api a localhost:8080 (gateway-ui) felé proxyzva
```

## Konténer

Az image nginx-szel szolgálja ki a buildet és a `/api`-t a gatewayre továbbítja.

| Változó | Jelentés |
| --- | --- |
| `API_UPSTREAM` | A felületi gateway címe (alap: `http://gateway-ui:8080`) |
| `KEYCLOAK_PUBLIC_URL` | A Keycloak böngészőből elérhető címe (a `/config.json`-ba kerül) |
| `APP_NAME` | Megjelenített név |

A betűkészletek (Roboto, Material Symbols) az image részei, internet nélkül (on-prem) is működik.

## Továbblépés: micro-frontendek

A szolgáltatásonkénti felületek (`src/app/features/<szolgáltatás>`) önálló, lustán betöltött egységek, közös
`core` és `shared` réteggel. Native Federationnel külön remote-okká bonthatók, így a szolgáltatások felülete
egymástól függetlenül telepíthető.
