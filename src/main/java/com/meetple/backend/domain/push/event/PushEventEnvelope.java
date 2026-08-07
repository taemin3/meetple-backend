package com.meetple.backend.domain.push.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record PushEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String occurredAt,
        String aggregateType,
        String aggregateId,
        JsonNode data
) {
}
