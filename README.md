# service/gateway – API Gateway

Spring Cloud Gateway (WebFlux). Ugyanabból az image-ből két példány fut:

| Mód (`GATEWAY_MODE`) | Útvonal | Cél |
| --- | --- | --- |
| `ui` | `/api/t/{tenant}/{szolgaltatas}/**`, `/api/admin/**`, `/api/tenants/{kod}` | A felhasználói és az admin felület belső API-ja |
| `public` | `/v1/t/{tenant}/{szolgaltatas}/**` → `/public/v1/t/{tenant}/...` | Publikus integrációs API, API kliensenkénti terheléskorláttal (Redis) |

Minden tenant-útvonalon ellenőrzi: a tenant aktív, a szolgáltatás engedélyezett nála (a `platform.tenants`
Kafka topicból), a token realmje megegyezik a tenanttal. Az `/api/admin/**` csak a `platform` realm tokenjével érhető el.
A válasz `X-Trace-Id` fejlécben adja vissza a műveleti azonosítót.

## Környezeti változók

`GATEWAY_MODE`, `KAFKA_BOOTSTRAP`, `REDIS_HOST`, `KEYCLOAK_PUBLIC_URL`, `KEYCLOAK_INTERNAL_URL`, `TENANT_ADMIN_URI`,
`IAM_URI`, `AUDIT_URI`, `PARTNER_URI`, `IRAT_URI`, `WEBHOOK_URI`, `RATE_LIMIT_RPS`, `RATE_LIMIT_BURST`, `OTLP_TRACES_ENDPOINT`.
