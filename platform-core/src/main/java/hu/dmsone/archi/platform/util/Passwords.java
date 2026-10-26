package hu.dmsone.archi.platform.util;

import java.security.SecureRandom;
import java.util.Base64;

/** Véletlen jelszavak és titkok generálása (URL- és SQL-biztos karakterkészlettel). */
public final class Passwords {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private Passwords() { }
}
