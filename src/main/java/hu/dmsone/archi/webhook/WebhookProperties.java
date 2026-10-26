package hu.dmsone.archi.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowInsecureHttp    csak fejlesztéshez: http:// célcím engedélyezése
 * @param maxAttempts          ennyi sikertelen próbálkozás után a kézbesítés DEAD
 * @param suspendAfterFailures ennyi egymást követő hiba után a feliratkozás felfüggesztődik
 */
@ConfigurationProperties(prefix = "webhook")
public record WebhookProperties(boolean allowInsecureHttp, int maxAttempts, int suspendAfterFailures) { }
