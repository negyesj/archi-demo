package hu.dmsone.archi.partner;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record Partner(
        UUID id,
        String name,
        String taxNumber,
        String registrationNumber,
        String country,
        String postalCode,
        String city,
        String address,
        String email,
        String phone,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /** Egysoros cím a pillanatképekhez (pl. iratban). */
    public String fullAddress() {
        StringBuilder sb = new StringBuilder();
        if (postalCode != null) { sb.append(postalCode).append(' '); }
        if (city != null) { sb.append(city); }
        if (address != null) { sb.append(sb.isEmpty() ? "" : ", ").append(address); }
        return sb.toString().trim();
    }

    public record Save(
            @NotBlank @Size(max = 300) String name,
            @Size(max = 30) @Pattern(regexp = "^$|^[0-9-]{8,30}$", message = "érvénytelen adószám") String taxNumber,
            @Size(max = 30) String registrationNumber,
            @Size(min = 2, max = 2) String country,
            @Size(max = 20) String postalCode,
            @Size(max = 100) String city,
            @Size(max = 300) String address,
            @Email @Size(max = 200) String email,
            @Size(max = 50) String phone,
            Long version) { }

    public record Summary(UUID id, String name, String taxNumber, String city, String address, boolean active) { }
}
