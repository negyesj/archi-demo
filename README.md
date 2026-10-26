# service/partner – Partner szolgáltatás

Partnertörzs tenantonként (`partner` séma a tenant adatbázisában). Minden írás az outboxon keresztül eseményt
küld (`domain.partner.events`): audit, riport (MongoDB `partner_partner`), webhook.

| API | Jogosultság |
| --- | --- |
| `GET /api/t/{tenant}/partner/partners?q&active&page&size` | `partner.read` |
| `GET /api/t/{tenant}/partner/partners/lookup?q=` – autocomplete más szolgáltatásoknak | bejelentkezés |
| `GET /api/t/{tenant}/partner/partners/{id}/summary` – pillanatképhez | bejelentkezés |
| `GET/POST/PUT/DELETE /api/t/{tenant}/partner/partners[/{id}]` | `partner.read` / `partner.write` |
| Publikus API v1: `/public/v1/t/{tenant}/partner/partners` – ETag/If-Match, Idempotency-Key | `partner.read` / `partner.write` |

A törlés logikai (inaktiválás), mert iratok hivatkozhatnak a partnerre. Más szolgáltatás a partner azonosítóját és a
rögzítéskori pillanatképét (név, cím) tárolja.
