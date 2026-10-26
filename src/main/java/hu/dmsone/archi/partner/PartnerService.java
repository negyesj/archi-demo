package hu.dmsone.archi.partner;

import hu.dmsone.archi.platform.event.EventEnvelope;
import hu.dmsone.archi.platform.outbox.OutboxWriter;
import hu.dmsone.archi.platform.web.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Partnertörzs. Minden írás eseményt rögzít az outboxba (audit, riport, webhook). Törlés = inaktiválás. */
@Service
public class PartnerService {

    public record Page(List<Partner> items, long total, int page, int size) { }

    private final PartnerRepository repository;
    private final OutboxWriter outbox;

    public PartnerService(PartnerRepository repository, OutboxWriter outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    public Page search(String q, Boolean active, int page, int size) {
        int s = Math.min(Math.max(size, 1), 200);
        int p = Math.max(page, 0);
        return new Page(repository.search(q, active, s, p * s), repository.count(q, active), p, s);
    }

    public List<Partner.Summary> lookup(String q, int limit) {
        return repository.search(q, true, Math.min(Math.max(limit, 1), 50), 0).stream().map(PartnerService::summary).toList();
    }

    public Partner get(UUID id) {
        return repository.find(id).orElseThrow(() -> ApiException.notFound("Partner"));
    }

    public static Partner.Summary summary(Partner p) {
        return new Partner.Summary(p.id(), p.name(), p.taxNumber(), p.city(), p.fullAddress(), p.active());
    }

    @Transactional
    public Partner create(Partner.Save req) {
        UUID id = UUID.randomUUID();
        repository.insert(id, req);
        Partner after = get(id);
        outbox.record("Partner", id.toString(), "CREATE", after.version(), null, after, EventEnvelope.Acl.forEveryone());
        return after;
    }

    @Transactional
    public Partner update(UUID id, Partner.Save req, long expectedVersion) {
        Partner before = get(id);
        if (!before.active()) {
            throw ApiException.conflict("Törölt partner nem módosítható");
        }
        if (repository.update(id, req, expectedVersion) == 0) {
            throw ApiException.preconditionFailed("A partnert közben más módosította (verzió: " + before.version() + ")");
        }
        Partner after = get(id);
        outbox.record("Partner", id.toString(), "UPDATE", after.version(), before, after, EventEnvelope.Acl.forEveryone());
        return after;
    }

    @Transactional
    public void delete(UUID id, Long expectedVersion) {
        Partner before = get(id);
        if (!before.active()) {
            return;
        }
        long v = expectedVersion == null ? before.version() : expectedVersion;
        if (repository.deactivate(id, v) == 0) {
            throw ApiException.preconditionFailed("A partnert közben más módosította (verzió: " + before.version() + ")");
        }
        Partner after = get(id);
        outbox.record("Partner", id.toString(), "DELETE", after.version(), before, after, EventEnvelope.Acl.forEveryone());
    }
}
