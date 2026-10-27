package hu.dmsone.archi.platform.trail;

/**
 * Az aktuális szál trail-kontextusa: melyik tenant melyik műveletén ({@code traceId}) belül, melyik lépés
 * ({@code spanId}) alatt fut a kód. Csak kontextuson belül rögzülnek beágyazott lépések (pl. SQL), így a háttérben
 * futó lekérdezések (outbox poll, webhook ütemező, migráció) nem kerülnek a trailbe.
 */
public final class TrailContext {

    public record Current(String tenant, String traceId, String spanId) { }

    private static final ThreadLocal<Current> CURRENT = new ThreadLocal<>();

    public static Current get() {
        return CURRENT.get();
    }

    static Current set(Current c) {
        Current previous = CURRENT.get();
        if (c == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(c);
        }
        return previous;
    }

    private TrailContext() { }
}
