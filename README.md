# platform/libs – közös Java könyvtárak

Ez a branch csak a backend szolgáltatások közös könyvtárait tartalmazza. Minden szolgáltatás ezeket használja,
így a tenant-kezelés, jogosultság, outbox, audit-esemény, naplózás egyszer készül el.

| Modul | Tartalom |
| --- | --- |
| `platform-parent` | Parent POM: Spring Boot 3.5, Spring Cloud 2025.0, Java 21, közös verziók |
| `platform-core` | Web-független elemek: `TenantRegistry` (Kafka `platform.tenants`), `TenantContext`, esemény boríték, transactional outbox, tenantonkénti PostgreSQL DataSource és Flyway migráció, titoktár, központi loglevel, JSON naplózás, szolgáltatás-manifest publikálás |
| `platform-web` | Servlet szolgáltatásoknak: Keycloak multi-realm JWT, tenant szűrő (`/api/t/{tenant}/…`, `/public/v1/t/{tenant}/…`), `@RequiresPermission` (IAM + Redis cache), RFC 9457 hibaválasz `traceId`-val, token relay RestClient |
| `platform-reporting` | Tenantonkénti MongoDB (`rpt_<tenant>`) és ACL szűrő a riport lekérdezésekhez |

## Build

```bash
mvn -B install
```

A szolgáltatás branchek a `platform-parent`-et `../platform-libs/platform-parent/pom.xml` relatív úton várják
(lásd a `main` branch `scripts/assemble.sh` szkriptjét), illetve a helyi Maven repóból.

## Fontos szabályok

- A jogosultság **nem** a felhő IAM-ből és nem a Keycloak szerepköreiből jön: a Keycloak csak hitelesít, a jogosultsági
  elemeket az IAM szolgáltatás adja (`PermissionService`).
- Üzleti adat írása mindig `@Transactional` metódusban, és az eseményt az `OutboxWriter` ugyanabban a tranzakcióban rögzíti.
- Felhő SDK nem kerülhet ide; a környezetfüggő elemek adapter mögött vannak (pl. `SecretProvider`).
