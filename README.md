# service/audit – Audit napló

Tenantonkénti alapszolgáltatás. Minden CRUD és üzleti eseményt a csomagütemezőn (Kafka: `domain.*.events`,
`iam.events`) keresztül, aszinkron módon fogad, és a tenant adatbázisának `audit` sémájába ír.

- Csak beszúrás: az alkalmazás DB-felhasználójától visszavonjuk az UPDATE/DELETE jogot.
- Idempotens: az `eventId` egyedi, ismételt kézbesítés nem duplikál.
- Mezőszintű változáslista (`changes`: mező, előtte, utána), és a `traceId`, amellyel a log is visszakereshető.

| API | Jogosultság |
| --- | --- |
| `GET /api/t/{tenant}/audit/events?from&to&service&entity&entityId&actor&action&q&page&size` | `audit.read` |
| `GET /api/t/{tenant}/audit/entities/{entity}/{entityId}` – egy entitás élettörténete | `audit.read` |
| `GET /public/v1/t/{tenant}/audit/events` – publikus API v1 | `audit.read` |
