package hu.dmsone.archi.platform.logging;

import java.time.Instant;

/**
 * Loglevel beállítás a {@code platform.loglevel} topicon.
 *
 * @param service   szolgáltatás azonosító vagy {@code *}
 * @param logger    logger név (csomag), null = ROOT
 * @param level     TRACE/DEBUG/INFO/WARN/ERROR, null = alapértelmezés visszaállítása
 * @param tenant    ha meg van adva, csak az adott tenant kéréseire érvényes
 * @param expiresAt lejárat után a beállítás érvénytelen
 */
public record LogLevelMessage(String service, String logger, String level, String tenant, Instant expiresAt) {

    public String key() {
        return service + "|" + (logger == null ? "ROOT" : logger) + "|" + (tenant == null ? "" : tenant);
    }

    public boolean expired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
