package hu.dmsone.archi.tenantadmin.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditEvent(UUID eventId, Instant occurredAt, String actorId, String actorName, String action,
                              String targetType, String targetId, JsonNode details) { }
