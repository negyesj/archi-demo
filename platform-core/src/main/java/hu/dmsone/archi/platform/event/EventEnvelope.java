package hu.dmsone.archi.platform.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Az összes CRUD / üzleti esemény közös borítéka (outbox → Kafka → audit, reporting, webhook).
 *
 * @param version     az entitás verziószáma; a fogyasztók csak nagyobb verzióval írják felül a vetületet
 * @param acl         kik láthatják a riport felületen (adatszintű jogosultság)
 * @param traceparent W3C trace kontextus a log-korrelációhoz
 */
public record EventEnvelope(
        UUID eventId,
        String tenantId,
        String service,
        String entity,
        String entityId,
        String action,
        long version,
        Actor actor,
        Instant occurredAt,
        JsonNode before,
        JsonNode after,
        Acl acl,
        String traceparent) {

    public record Actor(String id, String name, String type) {
        public static Actor system(String name) {
            return new Actor("system", name, "SYSTEM");
        }
    }

    /** Adatszintű láthatóság a riportban. {@code everyone=true}: a funkciójoggal rendelkezők mind láthatják. */
    public record Acl(List<String> users, List<String> orgUnits, boolean everyone) {
        public static Acl forEveryone() {
            return new Acl(List.of(), List.of(), true);
        }

        public static Acl of(List<String> users, List<String> orgUnits) {
            return new Acl(users == null ? List.of() : users, orgUnits == null ? List.of() : orgUnits, false);
        }
    }
}
