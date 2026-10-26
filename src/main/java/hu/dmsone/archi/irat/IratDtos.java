package hu.dmsone.archi.irat;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class IratDtos {

    public record ErkeztetesRequest(@NotNull LocalDate erkezesDatum, @NotNull UUID partnerId,
                                    @Size(max = 500) String targy) { }

    public record BontasRequest(@NotNull LocalDate bontasDatum, @NotNull @Min(0) Integer mellekletekSzama,
                                Long version) { }

    public record IktatasRequest(@NotNull UUID ugyintezoId, Long version) { }

    /** Érkeztetés, bontás és iktatás egy lépésben (egy tranzakció, három esemény). */
    public record EgylepesesRequest(@NotNull LocalDate erkezesDatum, @NotNull UUID partnerId,
                                    @Size(max = 500) String targy, @NotNull LocalDate bontasDatum,
                                    @NotNull @Min(0) Integer mellekletekSzama, @NotNull UUID ugyintezoId) { }

    private IratDtos() { }
}
