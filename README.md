# service/webhook – Webhook feliratkozás és kézbesítés

Tenantonkénti alapszolgáltatás. Külső rendszerek eseménytípusokra iratkozhatnak fel
(`<szolgáltatás>.<entitás>.<művelet>`, pl. `irat.kuldemeny.iktatas`, `partner.partner.update`, vagy `irat.*`, `*`).

- A Kafka eseményekből (`domain.*.events`, `iam.events`) kézbesítési sor képződik (`webhook.delivery`), a kiküldött
  üzenet publikus formátumú (nem a belső boríték), `id` = eseményazonosító (a fogadó ez alapján szűri a duplikátumot).
- Aláírás: `X-Webhook-Signature: sha256=<hex HMAC-SHA256(secret, X-Webhook-Timestamp + "." + body)>`. A titkot a
  létrehozáskor egyszer adjuk ki; a titoktárban tároljuk.
- Újrapróbálás exponenciális visszaállással (30 s → max. 6 óra), `WEBHOOK_MAX_ATTEMPTS` után `DEAD`; kézzel újraküldhető.
- `WEBHOOK_SUSPEND_AFTER` egymást követő hiba után a feliratkozás felfüggesztődik; módosításkor újraindul.
- Csak `https://` cél engedélyezett (`WEBHOOK_ALLOW_HTTP=true` csak fejlesztéshez).

| API | Jogosultság |
| --- | --- |
| `GET/POST /api/t/{tenant}/webhook/subscriptions`, `PUT/DELETE .../{id}` | `integration.manage` |
| `GET /api/t/{tenant}/webhook/deliveries?subscriptionId&status`, `POST .../{id}/retry` | `integration.manage` |
| Publikus API v1: `GET/POST/DELETE /public/v1/t/{tenant}/webhook/subscriptions` | `integration.manage` |
