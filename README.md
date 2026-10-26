# archi-demo – multi-tenant üzleti alkalmazás keretrendszer

Konténerizált, felhőfüggetlen SaaS platform: ugyanazok a Docker image-ek futnak Google Cloud Run-on és ügyfélnél
on-premise (Kubernetes vagy Docker Compose). Tenantonként külön PostgreSQL és MongoDB adatbázis; minden adatváltozás
transactional outboxon át Kafkára kerül, onnan táplálkozik az audit napló, a NoSQL riport és a webhook.

Architektúra terv: [docs/architecture.md](docs/architecture.md)

## Branch szerkezet

Minden külön futó komponens saját branchen van, hogy a kód ne keveredjen. A `main` csak az összerakást tartalmazza.

| Branch | Tartalom | Futtatás |
| --- | --- | --- |
| `main` | docker-compose, infra (Postgres init, Keycloak platform realm), szkriptek, integrációs CI, dokumentáció | – |
| `platform/libs` | közös Java könyvtárak: `platform-parent`, `platform-core`, `platform-web`, `platform-reporting` | – (Maven artefakt) |
| `service/gateway` | API Gateway (Spring Cloud Gateway): `ui` és `public` mód | HTTP |
| `service/tenant-admin` | tenantok, konfiguráció, katalógus, admin felhasználók, admin audit, loglevel | HTTP |
| `service/provisioner` | tenant létrehozás: PostgreSQL, MongoDB, Keycloak realm, első admin | worker |
| `service/iam` | felhasználó, csoport, szervezeti egység, jogosultság, API kliensek | HTTP |
| `service/audit` | audit napló (Kafka fogyasztó + kereső API) | HTTP + fogyasztó |
| `service/webhook` | webhook feliratkozás és aláírt kézbesítés | HTTP + fogyasztó |
| `service/partner` | partnertörzs, autocomplete, publikus API | HTTP |
| `service/irat` | érkeztetés, bontás, iktatás, iratkereső (NoSQL), publikus API | HTTP |
| `worker/outbox-relay` | outbox → Kafka, sorrendtartóan | worker |
| `worker/reporting-projector` | Kafka → MongoDB read modellek (tenantonként) | worker |
| `frontend/app` | felhasználói felület (Angular + Angular Material) | nginx |
| `frontend/admin` | tenant admin felület (Angular + Angular Material) | nginx |

Minden branch saját CI-t futtat (Java: Maven build + Docker image; frontend: Angular build + Docker image).

## Gyors indítás (Docker Compose)

```bash
git clone https://github.com/negyesj/archi-demo.git && cd archi-demo
./scripts/assemble.sh          # a brancheket a workspace/ mappába rakja (git worktree)
cp .env.example .env           # PLATFORM_SECRET_KEY: openssl rand -base64 32
docker compose up -d --build
./scripts/smoke-test.sh        # opcionális: tenant létrehozása és ellenőrzése
```

| Cím | Mi ez |
| --- | --- |
| http://localhost:4300 | Tenant admin felület – belépés: `admin` / `admin` (platform realm, csak fejlesztéshez) |
| http://localhost:4200 | Felhasználói felület – tenant kód megadása, majd a tenant saját login oldala |
| http://localhost:8090/v1/t/{tenant}/... | Publikus integrációs API (OAuth2 client credentials) |
| http://localhost:8180 | Keycloak |
| http://localhost:3000 | Grafana: logok (Loki) és trace-ek (Tempo), `traceId` alapján összekapcsolva |

### Első tenant

1. Tenant admin → Tenantok → Új tenant (pl. `acme`, szolgáltatások: Partner, Bejövő irat, első admin adatai).
2. Pár másodperc múlva az állapot `Aktív` → „Első admin jelszava” (egyszer olvasható ki).
3. http://localhost:4200 → `acme` → belépés az első adminnal (ideiglenes jelszó, első belépéskor csere).
4. Az első admin a „Rendszeradminisztrátorok” csoportban minden engedélyezett jogot megkap; innen lehet
   felhasználókat, csoportokat, szervezeti egységeket és jogosultságokat kezelni.

## Fő folyamatok

- **Tenant létrehozás:** tenant-admin → `platform.tenant.commands` → provisioner (DB-k, realm, első admin) →
  `platform.tenant.events` → `ACTIVE` → `platform.tenants` (compacted) → minden szolgáltatás a saját sémáit migrálja.
- **Adatváltozás:** szolgáltatás tranzakció (üzleti adat + outbox sor) → outbox-relay → `domain.<szolgáltatás>.events`
  → audit (PostgreSQL `audit` séma), reporting-projector (MongoDB `rpt_<tenant>`), webhook.
- **Jogosultság:** Keycloak csak hitelesít; a jogosultsági elemeket az IAM adja (`/iam/me/context`, Redis cache,
  változáskor azonnali érvénytelenítés). Nem függ a felhő IAM-től.
- **Naplózás:** JSON log `traceId`/`tenantId`/`userId` mezőkkel; a `traceparent` HTTP-n és Kafka fejlécben is
  továbbmegy, így egy művelet logjai szolgáltatásokon át összekapcsolhatók. Loglevel futás közben állítható
  (tenant admin → Naplózási szintek), akár egy tenantra szűkítve.

## Környezetek

| Komponens | Docker Compose | On-prem Kubernetes | GCP SaaS |
| --- | --- | --- | --- |
| Szolgáltatások, workerek | konténer | Deployment / Job | Cloud Run services / worker pools / jobs |
| PostgreSQL | postgres konténer | ügyfél PostgreSQL / CloudNativePG | Cloud SQL |
| MongoDB | mongo konténer | MongoDB Community / Enterprise | MongoDB Atlas |
| Kafka | kafka konténer (KRaft) | Kafka (Strimzi) | Managed Service for Apache Kafka |
| Keycloak | keycloak konténer | Keycloak (AD/LDAP federációval) | Keycloak konténer |
| Titkok | `.env` | Vault / Kubernetes Secret | Secret Manager |
| Log / trace | otel-lgtm | Loki, Tempo, Grafana | Cloud Logging, Cloud Trace |

## Ismert MVP egyszerűsítések

- A titoktár MVP implementációja a platform adatbázisban AES-GCM-mel titkosít (`SecretProvider`); élesben Vault /
  Secret Manager adapter.
- A szolgáltatások a platform adatbázist a titkok olvasásához közös felhasználóval érik el.
- A frontend egy alkalmazás, szolgáltatásonként lusta betöltésű modulokkal; a Native Federation micro-frontend
  bontás a következő lépés.
- A szervezeti egységre szűkített jogosultság-hozzárendelés (adat-szintű hatókör) és az audit hash-lánc még nincs kész.
- A `platform-cli` Keycloak kliens (direct grant) csak a füstteszthez van, élesben törlendő.
