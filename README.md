# service/provisioner – Tenant provisioner

Worker (HTTP szerver nélkül): a `platform.tenant.commands` topic `PROVISION` parancsait dolgozza fel.
GCP-n Cloud Run worker pool vagy Cloud Run job, on-prem Kubernetes Deployment/Job, Compose-ban konténer.

Lépések (mindegyik idempotens, újrafuttatható):

1. PostgreSQL: `t_<kod>_app` szerep és `t_<kod>` adatbázis (a szerep a tulajdonos), `pg_trgm` kiterjesztés.
2. MongoDB: `rpt_<kod>` adatbázis és `rpt_<kod>_app` felhasználó.
3. A jelszavak a titoktárba kerülnek (`SecretProvider`), soha nem a Kafkára.
4. Keycloak: `<kod>` realm a felhasználói felület `app` (publikus, PKCE) kliensével.
5. Első ügyfél-admin létrehozása ideiglenes jelszóval; a jelszó a titoktárba kerül, a tenant admin felületen egyszer olvasható ki.
6. `PROVISIONED` (vagy `FAILED` hibaüzenettel) a `platform.tenant.events` topicra.

A szolgáltatások sémáit nem a provisioner hozza létre: minden szolgáltatás a saját Flyway migrációit futtatja,
amikor a tenant aktív lesz (`TenantMigrator`), így a kódbázisok nem keverednek.
