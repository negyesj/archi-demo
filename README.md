# worker/outbox-relay – Outbox relay

Worker (HTTP szerver nélkül): minden aktív tenant adatbázisának `outbox.outbox_event` táblájából Kafkára publikál.
GCP-n Cloud Run worker pool, on-prem Kubernetes Deployment, Compose-ban konténer.

- Sorrendtartó: `seq` szerint, és tenantonként egyszerre egy példány dolgozik (`pg_try_advisory_xact_lock`), így
  több példány mellett sem cserélődik fel egy entitás két eseménye. Az üzenetkulcs `tenant:entitásId`, ezért egy
  entitás eseményei ugyanarra a partícióra kerülnek.
- Hibánál az adott tenant sora megáll és a következő körben újrapróbálja (`attempts`, `last_error`).
- A publikált sorokat 7 nap után törli. Nagyobb terhelésnél kiváltható Debezium CDC-vel (lásd architektúra terv).

Környezeti változók: `KAFKA_BOOTSTRAP`, `PLATFORM_DB_*`, `PLATFORM_SECRET_KEY`, `TENANT_DB_HOST`, `TENANT_DB_PORT`,
`RELAY_BATCH_SIZE`, `RELAY_POLL_MS`.
