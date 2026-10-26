package hu.dmsone.archi.iam.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    public record User(UUID id, String keycloakId, String username, String email, String fullName, String type,
                       boolean active, long version, Instant createdAt, Instant updatedAt) { }

    /** Rövid felhasználó adat választókhoz (pl. ügyintéző). */
    public record UserSummary(UUID id, String username, String fullName, String email, boolean active) { }

    public record CreateUser(
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-zA-Z0-9._@-]+$") String username,
            @Email String email,
            @NotBlank @Size(max = 200) String fullName) { }

    public record UpdateUser(@Email String email, @NotBlank @Size(max = 200) String fullName, boolean active,
                             long version) { }

    public record CreatedUser(User user, String temporaryPassword) { }

    public record Memberships(java.util.List<Ref> groups, java.util.List<Ref> orgUnits) { }

    public record Ref(UUID id, String name) { }

    private UserDtos() { }
}
