# service/irat – Bejövő irat kezelés

Érkeztetés → bontás → iktatás, lépésenként vagy egyben. A tranzakciós adat a tenant adatbázisának `irat`
sémájában van; a kereső felületet a NoSQL (MongoDB `irat_kuldemeny`) read modell szolgálja ki.

| Lépés | Bemenet | API (`/api/t/{tenant}/irat/...`) | Jogosultság |
| --- | --- | --- | --- |
| Érkeztetés | beérkezés dátuma, partner (Partner szolgáltatásból), tárgy | `POST /kuldemenyek` | `irat.erkeztetes` |
| Bontás | bontás dátuma, mellékletek száma | `POST /kuldemenyek/{id}/bontas` | `irat.bontas` |
| Iktatás | ügyintéző (IAM felhasználó); az iktatószámot a rendszer adja | `POST /kuldemenyek/{id}/iktatas` | `irat.iktatas` |
| Egylépéses | mind a fenti | `POST /kuldemenyek/egylepeses` | mindhárom |
| Munkalista | állapot szerint | `GET /kuldemenyek?allapot=` | `irat.read` |
| Iratkereső (NoSQL) | iktatószám, állapot, dátum, partner, ügyintéző, tárgy, mellékletszám | `GET /kereso` | `irat.report` (+ `irat.report.all`: minden látható) |

Publikus API v1: `/public/v1/t/{tenant}/irat/kuldemenyek` (Idempotency-Key a létrehozó hívásokon).

## Iktatószám

Egyedi és sorfolytonos: az `iktato_szamlalo` sorát az iktatás tranzakciójában növeljük (`insert ... on conflict do update
... returning`), ami a tranzakció végéig zárolja a sort; hiba esetén a növelés is visszagördül, így nincs lyuk. A
`(konyv, ev, sorszam)` és az `iktatoszam` egyedi index adatbázis-szinten zárja ki a duplikációt. A partner és az ügyintéző
ellenőrzése (távoli hívás) a zár megszerzése előtt történik.

Tenant beállítások (tenant admin): `iktatoszam.konyv` (alap: `IK`), `iktatoszam.minta` (alap: `{konyv}-{ev}/{sorszam}`),
`iktatoszam.szamjegy` (alap: 6) → pl. `IK-2026/000123`.

## Események

`domain.irat.events`: `ERKEZTETES`, `BONTAS`, `IKTATAS` – az egylépéses művelet is három külön eseményt ír, így az
auditban a három lépés külön látszik. Riport láthatóság (`_acl`): az érkeztető, az ügyintéző és az érkeztető szervezeti egységei.
