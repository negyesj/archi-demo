package hu.dmsone.archi.platform.trail;

import java.time.Instant;

/**
 * A műveleti trail egy lépése: egy szolgáltatásban mért, egy művelethez ({@code traceId}) tartozó időszelet.
 *
 * @param kind      a lépés típusa (lásd {@link TrailKind})
 * @param operation normalizált műveletnév, pl. {@code POST /api/t/{tenant}/irat/kuldemenyek} vagy {@code INSERT irat.kuldemeny}
 * @param target    az érintett erőforrás: adatbázis ({@code t_acme}), Kafka topic, MongoDB kollekció, URL host
 * @param queueMs   várakozási idő a lépés előtt (outbox → Kafka, Kafka → fogyasztó), ha értelmezett
 * @param e2eMs     a kiinduló üzleti művelettől (az esemény keletkezésétől) a lépés végéig eltelt idő, ha értelmezett
 * @param status    {@code OK} vagy {@code ERROR}
 * @param detail    rövid kiegészítés (HTTP státusz, SQL szöveg eleje, hibaüzenet)
 * @param root      a művelet belépési pontja (gateway)
 */
public record TrailStep(
        String tenant,
        String traceId,
        String spanId,
        String parentId,
        String service,
        String kind,
        String operation,
        String target,
        Instant startedAt,
        long durationMs,
        Long queueMs,
        Long e2eMs,
        String status,
        String detail,
        boolean root,
        String user) {
}
