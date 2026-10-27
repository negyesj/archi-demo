package hu.dmsone.archi.platform.trail;

/** A trail lépéstípusai. */
public final class TrailKind {
    /** Belépés a gatewayen (a művelet gyökere, a teljes válaszidő). */
    public static final String GATEWAY = "GATEWAY";
    /** HTTP kérés feldolgozása egy szolgáltatásban. */
    public static final String HTTP = "HTTP";
    /** SQL utasítás a tenant PostgreSQL adatbázisában. */
    public static final String SQL = "SQL";
    /** Esemény Kafkára küldése az outboxból (várakozás = outbox → Kafka). */
    public static final String KAFKA_PUBLISH = "KAFKA_PUBLISH";
    /** Esemény feldolgozása egy Kafka fogyasztóban (várakozás = Kafka → fogyasztó). */
    public static final String KAFKA_CONSUME = "KAFKA_CONSUME";
    /** Írás a tenant MongoDB riport adatbázisába. */
    public static final String MONGO = "MONGO";
    /** Webhook kézbesítése külső rendszernek. */
    public static final String WEBHOOK = "WEBHOOK";

    private TrailKind() { }
}
