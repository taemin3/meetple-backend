package com.meetple.backend.global.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.outbox.event.OutboxEventEnvelope;
import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import com.meetple.backend.domain.outbox.repository.OutboxEventRepository;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "meetple.performance.push-retry",
        name = "enabled",
        havingValue = "true"
)
public class PushRetryMeasurementService {

    private static final String TOPIC = "meetple.push.notification.v1";
    private static final String DLT = TOPIC + ".dlq";

    private final OutboxEventPublisher outboxEventPublisher;
    private final OutboxEventRepository outboxEventRepository;
    private final PushDeviceTokenService pushDeviceTokenService;
    private final PushRetryMeasurementSender sender;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<UUID>> created = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, ReplayRecord>> dltRecords = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, MainTopicRecord>> mainTopicRecords = new ConcurrentHashMap<>();

    @Transactional
    public UUID create(Long memberId, String runId, int index) {
        validate(runId, index);
        if (pushDeviceTokenService.findTargets(List.of(memberId)).isEmpty()) {
            throw new IllegalStateException("The measurement member must have a registered push token.");
        }

        UUID eventId = outboxEventPublisher.publish(new OutboxEventRequest(
                "push-retry-measurement",
                runId,
                PushRetryMeasurementSender.EVENT_TYPE,
                "push-retry-measurement:" + runId + ":" + index,
                OutboxEventTopic.PUSH_NOTIFICATION,
                1,
                "push-retry-measurement:" + runId + ":" + index,
                Map.of(
                        "recipientMemberId", memberId,
                        "title", "Push retry measurement",
                        "body", "run=" + runId + ", index=" + index
                )
        ));
        created.computeIfAbsent(runId, ignored -> ConcurrentHashMap.newKeySet()).add(eventId);
        return eventId;
    }

    @KafkaListener(
            topics = DLT,
            groupId = "${MEETPLE_PERFORMANCE_PUSH_RETRY_DLT_GROUP:meetple-push-retry-measurement-dlt-v1}"
    )
    public void collectDlt(ConsumerRecord<String, String> record) {
        OutboxEventEnvelope envelope = readEnvelope(record.value());
        if (envelope == null) {
            return;
        }
        if (!PushRetryMeasurementSender.EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }
        dltRecords.computeIfAbsent(envelope.aggregateId(), ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(envelope.eventId(), new ReplayRecord(record.key(), record.value()));
    }

    @KafkaListener(
            topics = TOPIC,
            groupId = "${MEETPLE_PERFORMANCE_PUSH_RETRY_CDC_GROUP:meetple-push-retry-measurement-cdc-v1}"
    )
    public void collectMainTopic(ConsumerRecord<String, String> record) {
        OutboxEventEnvelope envelope = readEnvelope(record.value());
        if (envelope == null || !PushRetryMeasurementSender.EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }

        Instant receivedAt = Instant.now();
        Instant occurredAt;
        try {
            occurredAt = Instant.parse(envelope.occurredAt());
        } catch (Exception ignored) {
            return;
        }
        long latencyMillis = Math.max(0, Duration.between(occurredAt, receivedAt).toMillis());
        mainTopicRecords.computeIfAbsent(envelope.aggregateId(), ignored -> new ConcurrentHashMap<>())
                .compute(envelope.eventId(), (ignored, existing) -> existing == null
                        ? new MainTopicRecord(latencyMillis, 1)
                        : new MainTopicRecord(existing.latencyMillis(), existing.deliveries() + 1));
    }

    public RunStatus status(String runId) {
        Set<UUID> eventIds = created.getOrDefault(runId, Set.of());
        int dltCount = dltRecords.getOrDefault(runId, Map.of()).size();
        Map<UUID, MainTopicRecord> mainRecords = mainTopicRecords.getOrDefault(runId, Map.of());
        List<Long> latencies = mainRecords.entrySet().stream()
                .filter(entry -> eventIds.contains(entry.getKey()))
                .map(entry -> entry.getValue().latencyMillis())
                .sorted()
                .toList();
        int duplicateMainTopicEvents = mainRecords.entrySet().stream()
                .filter(entry -> eventIds.contains(entry.getKey()))
                .mapToInt(entry -> Math.max(0, entry.getValue().deliveries() - 1))
                .sum();
        long committedOutboxEvents = outboxEventRepository
                .countByAggregateTypeAndAggregateIdAndEventType(
                        "push-retry-measurement",
                        runId,
                        PushRetryMeasurementSender.EVENT_TYPE
                );
        return new RunStatus(
                runId,
                sender.mode().name(),
                eventIds.size(),
                committedOutboxEvents,
                latencies.size(),
                duplicateMainTopicEvents,
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                percentile(latencies, 0.99),
                latencies.isEmpty() ? null : latencies.getLast(),
                sender.attempts(eventIds),
                dltCount,
                sender.successes(eventIds),
                sender.duplicateSuccesses(eventIds)
        );
    }

    public RunStatus replay(String runId) {
        Set<UUID> eventIds = created.getOrDefault(runId, Set.of());
        Map<UUID, ReplayRecord> records = dltRecords.getOrDefault(runId, Map.of());
        if (eventIds.isEmpty() || records.size() != eventIds.size()) {
            throw new IllegalStateException("All created events must reach the DLT before replay.");
        }
        sender.setMode(PushRetryMeasurementSender.Mode.SUCCESS);
        records.values().forEach(record -> kafkaTemplate.send(TOPIC, record.key(), record.value()).join());
        return status(runId);
    }

    public RunStatus fail(String runId) {
        sender.setMode(PushRetryMeasurementSender.Mode.FAIL);
        return status(runId);
    }

    public RunStatus success(String runId) {
        sender.setMode(PushRetryMeasurementSender.Mode.SUCCESS);
        return status(runId);
    }

    private OutboxEventEnvelope readEnvelope(String value) {
        try {
            return objectMapper.readValue(value, OutboxEventEnvelope.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, index));
    }

    private void validate(String runId, int index) {
        if (runId == null || !runId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("runId must contain 1-64 letters, digits, or hyphens.");
        }
        if (index < 0 || index >= 10_000) {
            throw new IllegalArgumentException("index must be between 0 and 9999.");
        }
    }

    private record ReplayRecord(String key, String value) {
    }

    private record MainTopicRecord(long latencyMillis, int deliveries) {
    }

    public record RunStatus(
            String runId,
            String mode,
            int createdEvents,
            long committedOutboxEvents,
            int mainTopicEvents,
            int duplicateMainTopicEvents,
            Long cdcLatencyP50Ms,
            Long cdcLatencyP95Ms,
            Long cdcLatencyP99Ms,
            Long cdcLatencyMaxMs,
            int sendAttempts,
            int dltEvents,
            int successfulEvents,
            int duplicateSuccesses
    ) {
    }
}
