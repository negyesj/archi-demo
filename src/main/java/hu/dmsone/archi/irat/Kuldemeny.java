package hu.dmsone.archi.irat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Bejövő küldemény (érkeztetett → bontott → iktatott). */
public record Kuldemeny(
        UUID id,
        Allapot allapot,
        LocalDate erkezesDatum,
        String targy,
        UUID partnerId,
        String partnerNev,
        String partnerCim,
        String erkeztetteId,
        String erkeztetteNev,
        LocalDate bontasDatum,
        Integer mellekletekSzama,
        String bontottaId,
        String bontottaNev,
        String iktatoszam,
        Instant iktatasIdeje,
        UUID ugyintezoId,
        String ugyintezoNev,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public enum Allapot { ERKEZTETETT, BONTOTT, IKTATOTT }
}
