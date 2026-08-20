package com.meetple.backend.domain.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.image.service.ImageDeletionService;
import com.meetple.backend.domain.outbox.entity.OutboxEvent;
import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import com.meetple.backend.domain.outbox.repository.OutboxEventRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class OutboxEventPublisherIntegrationTest {

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ImageDeletionService imageDeletionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        outboxEventRepository.deleteAll();
    }

    @Test
    void publishStoresRoutingMetadataAndVersionedEnvelope() {
        OutboxEventRequest request = request("notification:participation-approved:101:7");

        UUID eventId = transactionTemplate.execute(status -> outboxEventPublisher.publish(request));

        OutboxEvent saved = outboxEventRepository.findById(eventId).orElseThrow();
        assertThat(saved.getAggregateType()).isEqualTo("notification");
        assertThat(saved.getAggregateId()).isEqualTo("101");
        assertThat(saved.getEventType()).isEqualTo("PARTICIPATION_APPROVED");
        assertThat(saved.getEventKey()).isEqualTo("member:7");
        assertThat(saved.getTopic()).isEqualTo("meetple.push.notification.v1");
        assertThat(saved.getSchemaVersion()).isEqualTo(1);
        assertThat(saved.getDeduplicationKey()).isEqualTo("notification:participation-approved:101:7");
        assertThat(saved.getPayload().path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(saved.getPayload().path("eventType").asText()).isEqualTo("PARTICIPATION_APPROVED");
        assertThat(saved.getPayload().path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(saved.getPayload().path("occurredAt").asText()).isNotBlank();
        assertThat(saved.getPayload().path("aggregateType").asText()).isEqualTo("notification");
        assertThat(saved.getPayload().path("aggregateId").asText()).isEqualTo("101");
        assertThat(saved.getPayload().path("data").path("recipientMemberId").asLong()).isEqualTo(7L);
    }

    @Test
    void publishRequiresAnExistingBusinessTransaction() {
        OutboxEventRequest request = request("notification:participation-approved:102:7");

        assertThatThrownBy(() -> outboxEventPublisher.publish(request))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void businessTransactionRollbackAlsoRollsBackOutboxEvent() {
        OutboxEventRequest request = request("notification:participation-approved:103:7");

        transactionTemplate.executeWithoutResult(status -> {
            outboxEventPublisher.publish(request);
            status.setRollbackOnly();
        });

        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void duplicateDeduplicationKeyIsRejectedByTheDatabase() {
        OutboxEventRequest request = request("notification:participation-approved:104:7");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            outboxEventPublisher.publish(request);
            outboxEventPublisher.publish(request);
            outboxEventRepository.flush();
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void imageDeletionRequestIsStoredAsKafkaRoutableOutboxEvent() {
        String objectKey = "images/profile/7/old.png";

        transactionTemplate.executeWithoutResult(status -> imageDeletionService.schedule(objectKey));

        OutboxEvent saved = outboxEventRepository.findAll().getFirst();
        assertThat(saved.getAggregateType()).isEqualTo("image");
        assertThat(saved.getAggregateId()).isEqualTo(objectKey);
        assertThat(saved.getEventType()).isEqualTo("IMAGE_DELETE_REQUESTED");
        assertThat(saved.getEventKey()).isEqualTo(objectKey);
        assertThat(saved.getTopic()).isEqualTo("meetple.image.delete.v1");
        assertThat(saved.getPayload().path("data").path("objectKey").asText())
                .isEqualTo(objectKey);
    }

    private OutboxEventRequest request(String deduplicationKey) {
        return new OutboxEventRequest(
                "notification",
                "101",
                "PARTICIPATION_APPROVED",
                "member:7",
                OutboxEventTopic.PUSH_NOTIFICATION,
                1,
                deduplicationKey,
                Map.of(
                        "recipientMemberId", 7L,
                        "meetingId", 101L,
                        "title", "참여 신청이 승인되었어요"
                )
        );
    }
}
