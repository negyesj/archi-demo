# worker/reporting-projector – NoSQL riport szinkron

Worker: a `domain.*.events` és `iam.events` eseményekből aszinkron módon frissíti a tenant MongoDB riport
adatbázisát (`rpt_<tenant>`). Kollekció: `<szolgáltatás>_<entitás>` (pl. `irat_kuldemeny`, `partner_partner`).

- Dokumentum = az entitás utolsó állapota (`after`) + `_acl` (adatszintű láthatóság) + `_version`, `_updatedAt`.
- Csak nagyobb verzió ír felül (idempotens, sorrendtűrő); törlésnél a dokumentum eltávolításra kerül.
- Tenantonként külön adatbázis és külön MongoDB felhasználó (a titoktárból).
- Denormalizálás: partner módosításakor az iratok `partnerAktualisNev` mezője frissül.

Újraépítés (pl. új riport mező): a `reporting-projector` consumer group offsetjét a topicok elejére kell állítani
(`kafka-consumer-groups.sh --reset-offsets --to-earliest --group reporting-projector --all-topics --execute`),
a `domain.*` topicok megőrzési ideje ezért végtelen.
