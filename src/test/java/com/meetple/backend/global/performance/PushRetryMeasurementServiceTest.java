package com.meetple.backend.global.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.outbox.event.OutboxEventEnvelope;
import com.meetple.backend.domain.outbox.repository.OutboxEventRepository;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class PushRetryMeasurementServiceTest {

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PushDeviceTokenService pushDeviceTokenService;

    @Mock
    private PushRetryMeasurementSender sender;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private PushRetryMeasurementService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new PushRetryMeasurementService(
                outboxEventPublisher,
                outboxEventRepository,
                pushDeviceTokenService,
                sender,
                kafkaTemplate,
                objectMapper
        );
    }

    @Test
    void statusComparesCommittedOutboxRowsWithUniqueMainTopicEvents() throws Exception {
        String runId = "outbox-cdc-test";
        UUID eventId = UUID.randomUUID();
        given(pushDeviceTokenService.findTargets(List.of(7L)))
                .willReturn(List.of(new PushDeviceTarget(1L, "test-token")));
        given(outboxEventPublisher.publish(org.mockito.ArgumentMatchers.any(OutboxEventRequest.class)))
                .willReturn(eventId);
        given(outboxEventRepository.countByAggregateTypeAndAggregateIdAndEventType(
                "push-retry-measurement",
                runId,
                PushRetryMeasurementSender.EVENT_TYPE
        )).willReturn(1L);
        given(sender.mode()).willReturn(PushRetryMeasurementSender.Mode.SUCCESS);

        service.create(7L, runId, 0);
        String payload = objectMapper.writeValueAsString(new OutboxEventEnvelope(
                eventId,
                PushRetryMeasurementSender.EVENT_TYPE,
                1,
                Instant.now().minusSeconds(1).toString(),
                "push-retry-measurement",
                runId,
                objectMapper.valueToTree(Map.of("recipientMemberId", 7L))
        ));
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "meetple.push.notification.v1",
                0,
                1L,
                "key",
                payload
        );

        service.collectMainTopic(record);
        service.collectMainTopic(record);

        PushRetryMeasurementService.RunStatus status = service.status(runId);
        assertThat(status.createdEvents()).isEqualTo(1);
        assertThat(status.committedOutboxEvents()).isEqualTo(1);
        assertThat(status.mainTopicEvents()).isEqualTo(1);
        assertThat(status.duplicateMainTopicEvents()).isEqualTo(1);
        assertThat(status.cdcLatencyP50Ms()).isGreaterThanOrEqualTo(900L);
        assertThat(status.cdcLatencyP95Ms()).isEqualTo(status.cdcLatencyP50Ms());
        assertThat(status.cdcLatencyP99Ms()).isEqualTo(status.cdcLatencyP50Ms());
        assertThat(status.cdcLatencyMaxMs()).isEqualTo(status.cdcLatencyP50Ms());
    }
}
