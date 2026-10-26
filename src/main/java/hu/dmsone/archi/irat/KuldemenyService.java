package hu.dmsone.archi.irat;

import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.outbox.ActorProvider;
import hu.dmsone.archi.platform.outbox.OutboxWriter;
import hu.dmsone.archi.platform.tenant.TenantContext;
import hu.dmsone.archi.platform.tenant.TenantInfo;
import hu.dmsone.archi.platform.tenant.TenantRegistry;
import hu.dmsone.archi.platform.web.error.ApiException;
import hu.dmsone.archi.platform.web.permission.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bejövő küldemény életciklusa: érkeztetés → bontás → iktatás, külön lépésenként vagy egyben.
 * Minden lépés külön eseményt rögzít az outboxba (audit, riport, webhook). Az egylépéses művelet egyetlen
 * tranzakció (minden vagy semmi), de az auditban a három lépés külön látszik.
 */
@Service
public class KuldemenyService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

    private final KuldemenyRepository repository;
    private final RemoteLookups lookups;
    private final OutboxWriter outbox;
    private final ActorProvider actors;
    private final PermissionService permissions;
    private final TenantRegistry registry;
    private final TransactionTemplate tx;

    public KuldemenyService(KuldemenyRepository repository, RemoteLookups lookups, OutboxWriter outbox,
                            ActorProvider actors, PermissionService permissions, TenantRegistry registry,
                            TransactionTemplate tx) {
        this.repository = repository;
        this.lookups = lookups;
        this.outbox = outbox;
        this.actors = actors;
        this.permissions = permissions;
        this.registry = registry;
        this.tx = tx;
    }

    public Kuldemeny get(UUID id) {
        return repository.find(id).orElseThrow(() -> ApiException.notFound("Küldemény"));
    }

    public List<Kuldemeny> list(Kuldemeny.Allapot allapot, int limit) {
        return repository.list(allapot, Math.min(Math.max(limit, 1), 500));
    }

    // ---- Érkeztetés ----

    public Kuldemeny erkeztet(IratDtos.ErkeztetesRequest req) {
        RemoteLookups.PartnerSummary partner = lookups.activePartner(req.partnerId());
        List<String> orgUnits = List.copyOf(permissions.current().orgUnits());
        return tx.execute(s -> doErkeztet(UUID.randomUUID(), req, partner, orgUnits));
    }

    private Kuldemeny doErkeztet(UUID id, IratDtos.ErkeztetesRequest req, RemoteLookups.PartnerSummary partner,
                                 List<String> orgUnits) {
        if (req.erkezesDatum().isAfter(java.time.LocalDate.now(ZONE))) {
            throw ApiException.badRequest("A beérkezés dátuma nem lehet jövőbeli");
        }
        EventEnvelope.Actor actor = actors.current();
        repository.insertErkeztetett(id, req, partner.name(), partner.address(), actor.id(), actor.name(), orgUnits);
        Kuldemeny after = get(id);
        outbox.record("Kuldemeny", id.toString(), "ERKEZTETES", after.version(), null, after, acl(after, orgUnits));
        return after;
    }

    // ---- Bontás ----

    public Kuldemeny bont(UUID id, IratDtos.BontasRequest req) {
        return tx.execute(s -> doBont(id, req));
    }

    private Kuldemeny doBont(UUID id, IratDtos.BontasRequest req) {
        Kuldemeny before = get(id);
        if (before.allapot() != Kuldemeny.Allapot.ERKEZTETETT) {
            throw ApiException.conflict("Csak érkeztetett küldemény bontható (jelenlegi állapot: " + before.allapot() + ")");
        }
        if (req.bontasDatum().isBefore(before.erkezesDatum())) {
            throw ApiException.badRequest("A bontás dátuma nem lehet korábbi a beérkezésnél");
        }
        long version = req.version() == null ? before.version() : req.version();
        EventEnvelope.Actor actor = actors.current();
        if (repository.updateBontott(id, req, actor.id(), actor.name(), version) == 0) {
            throw ApiException.preconditionFailed("A küldeményt közben más módosította");
        }
        Kuldemeny after = get(id);
        outbox.record("Kuldemeny", id.toString(), "BONTAS", after.version(), before, after,
                acl(after, repository.aclOrgUnits(id)));
        return after;
    }

    // ---- Iktatás ----

    public Kuldemeny iktat(UUID id, IratDtos.IktatasRequest req) {
        RemoteLookups.UserSummary ugyintezo = lookups.activeUser(req.ugyintezoId());
        return tx.execute(s -> doIktat(id, req.version(), ugyintezo));
    }

    private Kuldemeny doIktat(UUID id, Long expectedVersion, RemoteLookups.UserSummary ugyintezo) {
        Kuldemeny before = get(id);
        if (before.allapot() != Kuldemeny.Allapot.BONTOTT) {
            throw ApiException.conflict("Csak bontott küldemény iktatható (jelenlegi állapot: " + before.allapot() + ")");
        }
        TenantInfo tenant = registry.find(TenantContext.require()).orElseThrow();
        String konyv = tenant.setting("iktatoszam.konyv", "IK");
        int ev = Instant.now().atZone(ZONE).getYear();
        long sorszam = repository.nextSorszam(konyv, ev);
        String iktatoszam = format(tenant.setting("iktatoszam.minta", "{konyv}-{ev}/{sorszam}"),
                konyv, ev, sorszam, Integer.parseInt(tenant.setting("iktatoszam.szamjegy", "6")));
        long version = expectedVersion == null ? before.version() : expectedVersion;
        if (repository.updateIktatott(id, konyv, ev, sorszam, iktatoszam, ugyintezo.id(), ugyintezo.fullName(), version) == 0) {
            throw ApiException.preconditionFailed("A küldeményt közben más módosította");
        }
        Kuldemeny after = get(id);
        outbox.record("Kuldemeny", id.toString(), "IKTATAS", after.version(), before, after,
                acl(after, repository.aclOrgUnits(id)));
        return after;
    }

    static String format(String pattern, String konyv, int ev, long sorszam, int digits) {
        String padded = String.format("%0" + Math.max(1, Math.min(digits, 12)) + "d", sorszam);
        return pattern.replace("{konyv}", konyv).replace("{ev}", Integer.toString(ev)).replace("{sorszam}", padded);
    }

    // ---- Egylépéses ----

    public Kuldemeny egylepeses(IratDtos.EgylepesesRequest req) {
        RemoteLookups.PartnerSummary partner = lookups.activePartner(req.partnerId());
        RemoteLookups.UserSummary ugyintezo = lookups.activeUser(req.ugyintezoId());
        List<String> orgUnits = List.copyOf(permissions.current().orgUnits());
        return tx.execute(s -> {
            UUID id = UUID.randomUUID();
            Kuldemeny k = doErkeztet(id, new IratDtos.ErkeztetesRequest(req.erkezesDatum(), req.partnerId(), req.targy()),
                    partner, orgUnits);
            k = doBont(id, new IratDtos.BontasRequest(req.bontasDatum(), req.mellekletekSzama(), k.version()));
            return doIktat(id, k.version(), ugyintezo);
        });
    }

    /** Riport láthatóság: az érkeztető és az ügyintéző, valamint az érkeztető szervezeti egységei. */
    private static EventEnvelope.Acl acl(Kuldemeny k, List<String> orgUnits) {
        List<String> users = new ArrayList<>();
        if (k.erkeztetteId() != null) {
            users.add(k.erkeztetteId());
        }
        if (k.ugyintezoId() != null) {
            users.add(k.ugyintezoId().toString());
        }
        return EventEnvelope.Acl.of(users, orgUnits);
    }
}
