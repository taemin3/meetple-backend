package com.meetple.backend.domain.outbox.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record OutboxEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String occurredAt,
        String aggregateType,
        String aggregateId,
        JsonNode data
) {
}
