package hu.dmsone.archi.platform.outbox;

import hu.dmsone.archi.platform.event.EventEnvelope;

/** Az aktuális végrehajtó (felhasználó, API kliens vagy rendszer) az eseményekhez és az audithoz. */
public interface ActorProvider {
    EventEnvelope.Actor current();
}
