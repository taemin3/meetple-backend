package com.meetple.backend.domain.outbox.service;

import com.meetple.backend.domain.push.event.PushEventTopic;
import java.util.Objects;

public record OutboxEventRequest(
        String aggregateType,
        String aggregateId,
        String eventType,
        String eventKey,
        PushEventTopic topic,
        int schemaVersion,
        String deduplicationKey,
        Object data
) {

    public OutboxEventRequest {
        aggregateType = requireText(aggregateType, "aggregateType", 100);
        aggregateId = requireText(aggregateId, "aggregateId", 255);
        eventType = requireText(eventType, "eventType", 100);
        eventKey = requireText(eventKey, "eventKey", 255);
        topic = Objects.requireNonNull(topic, "topic must not be null");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be greater than 0");
        }
        deduplicationKey = requireText(deduplicationKey, "deduplicationKey", 255);
        data = Objects.requireNonNull(data, "data must not be null");
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
