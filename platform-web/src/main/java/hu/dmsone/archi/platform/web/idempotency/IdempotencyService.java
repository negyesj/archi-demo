package hu.dmsone.archi.platform.web.idempotency;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.platform.web.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code Idempotency-Key} kezelés a publikus API létrehozó hívásaihoz: ugyanazzal a kulccsal ismételt kérés az első
 * választ kapja vissza (pl. hálózati hiba utáni újrapróbálás nem iktat kétszer). A kulcs hívónként (token subject) egyedi.
 * A tábla a szolgáltatás saját sémájában van: {@code <schema>.idempotency_key}.
 */
public class IdempotencyService {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final String table;

    public IdempotencyService(DataSource tenantDataSource, TransactionTemplate tx, ObjectMapper objectMapper, String schema) {
        this.jdbc = JdbcClient.create(tenantDataSource);
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.table = schema + ".idempotency_key";
    }

    private record Stored(String requestHash, Integer status, String body) { }

    public <T> ResponseEntity<T> execute(String key, Object request, Class<T> type, Supplier<ResponseEntity<T>> action) {
        if (key == null || key.isBlank()) {
            return tx.execute(s -> action.get());
        }
        if (key.length() > 200) {
            throw ApiException.badRequest("Az Idempotency-Key legfeljebb 200 karakter lehet");
        }
        String client = Optional.ofNullable(CurrentUser.subject()).orElse("anonymous");
        String hash = hash(request);
        return tx.execute(status -> {
            int inserted = jdbc.sql("insert into " + table + " (client, key, request_hash) values (:c, :k, :h) on conflict do nothing")
                    .param("c", client).param("k", key).param("h", hash).update();
            if (inserted == 0) {
                Stored stored = jdbc.sql("select request_hash, response_status, response_body::text as body from " + table
                                + " where client = :c and key = :k")
                        .param("c", client).param("k", key)
                        .query((rs, i) -> new Stored(rs.getString(1), (Integer) rs.getObject(2), rs.getString(3))).single();
                if (!stored.requestHash().equals(hash)) {
                    throw ApiException.unprocessable("Ez az Idempotency-Key már egy másik kéréshez tartozik");
                }
                if (stored.status() == null) {
                    throw ApiException.conflict("Ugyanezzel az Idempotency-Key-jel egy kérés még folyamatban van");
                }
                return ResponseEntity.status(stored.status()).header("Idempotent-Replayed", "true")
                        .body(read(stored.body(), type));
            }
            ResponseEntity<T> response = action.get();
            jdbc.sql("update " + table + " set response_status = :s, response_body = cast(:b as jsonb) where client = :c and key = :k")
                    .param("s", response.getStatusCode().value()).param("b", write(response.getBody()))
                    .param("c", client).param("k", key).update();
            return response;
        });
    }

    private String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(write(request == null ? Map.of() : request)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            JavaType t = objectMapper.getTypeFactory().constructType(type);
            return json == null ? null : objectMapper.readValue(json, t);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A szolgáltatás migrációjába illesztendő tábla. */
    public static final String DDL = """
            create table idempotency_key (
                client          varchar(200) not null,
                key             varchar(200) not null,
                request_hash    varchar(64)  not null,
                response_status int,
                response_body   jsonb,
                created_at      timestamptz  not null default now(),
                primary key (client, key)
            );
            """;

    public static HttpStatus statusOf(ResponseEntity<?> r) {
        return HttpStatus.valueOf(r.getStatusCode().value());
    }
}
