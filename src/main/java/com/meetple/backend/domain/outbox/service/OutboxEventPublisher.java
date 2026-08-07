package com.meetple.backend.domain.outbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.outbox.entity.OutboxEvent;
import com.meetple.backend.domain.outbox.repository.OutboxEventRepository;
import com.meetple.backend.domain.push.event.PushEventEnvelope;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(OutboxEventRequest request) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        JsonNode data = objectMapper.valueToTree(request.data());
        JsonNode payload = objectMapper.valueToTree(new PushEventEnvelope(
                eventId,
                request.eventType(),
                request.schemaVersion(),
                occurredAt.toString(),
                request.aggregateType(),
                request.aggregateId(),
                data
        ));

        OutboxEvent event = OutboxEvent.create(
                eventId,
                request.aggregateType(),
                request.aggregateId(),
                request.eventType(),
                request.eventKey(),
                request.topic().getValue(),
                request.schemaVersion(),
                payload,
                occurredAt,
                request.deduplicationKey()
        );
        outboxEventRepository.save(event);
        return eventId;
    }
}
