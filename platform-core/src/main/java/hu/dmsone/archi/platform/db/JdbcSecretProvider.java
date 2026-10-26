package hu.dmsone.archi.platform.db;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Titkok a platform adatbázis {@code secret} táblájában, AES-256-GCM titkosítással. */
public class JdbcSecretProvider implements SecretProvider {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final JdbcTemplate jdbc;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public JdbcSecretProvider(DataSource platformDataSource, String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("platform.secrets.key nincs beállítva");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalStateException("platform.secrets.key 32 bájtos (256 bites) kulcs kell legyen");
        }
        this.jdbc = new JdbcTemplate(platformDataSource);
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public Optional<String> get(String name) {
        String cached = cache.get(name);
        if (cached != null) {
            return Optional.of(cached);
        }
        List<String> values = jdbc.query("select nonce, value_enc from secret where key = ?",
                (rs, i) -> decrypt(rs.getBytes(1), rs.getBytes(2)), name);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        cache.put(name, values.get(0));
        return Optional.of(values.get(0));
    }

    @Override
    public void put(String name, String value) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] enc = encrypt(nonce, value);
        jdbc.update("""
                insert into secret (key, nonce, value_enc, updated_at) values (?, ?, ?, now())
                on conflict (key) do update set nonce = excluded.nonce, value_enc = excluded.value_enc, updated_at = now()
                """, name, nonce, enc);
        cache.put(name, value);
    }

    @Override
    public void delete(String name) {
        jdbc.update("delete from secret where key = ?", name);
        cache.remove(name);
    }

    private byte[] encrypt(byte[] nonce, String value) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Titok titkosítása sikertelen", e);
        }
    }

    private String decrypt(byte[] nonce, byte[] enc) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(c.doFinal(enc), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Titok visszafejtése sikertelen", e);
        }
    }
}
