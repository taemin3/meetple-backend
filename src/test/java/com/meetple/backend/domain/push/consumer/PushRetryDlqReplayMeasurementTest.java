package com.meetple.backend.domain.push.consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.meetple.backend.domain.chat.service.ChatNotificationSettingService;
import com.meetple.backend.domain.push.delivery.PushDeliveryClaim;
import com.meetple.backend.domain.push.delivery.PushDeliveryService;
import com.meetple.backend.domain.push.fcm.PushMessage;
import com.meetple.backend.domain.push.fcm.PushMessageSender;
import com.meetple.backend.domain.push.fcm.PushSendFailure;
import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "push.kafka.consumer-enabled=true",
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:meetple-push-retry-100;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "push.kafka.retry.initial-delay-ms=10",
        "push.kafka.retry.multiplier=4.0",
        "push.kafka.retry.jitter-ms=0",
        "push.kafka.retry.max-delay-ms=640",
        "PUSH_KAFKA_CONSUMER_GROUP=meetple-push-retry-100-measurement",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.org.springframework.kafka=OFF",
        "logging.level.com.meetple.backend.domain.push.consumer.PushKafkaConsumer=OFF"
})
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 3,
        topics = {
                PushRetryDlqReplayMeasurementTest.TOPIC,
                PushRetryDlqReplayMeasurementTest.TOPIC + ".retry-0",
                PushRetryDlqReplayMeasurementTest.TOPIC + ".retry-1",
                PushRetryDlqReplayMeasurementTest.TOPIC + ".retry-2",
                PushRetryDlqReplayMeasurementTest.TOPIC + ".retry-3",
                PushRetryDlqReplayMeasurementTest.DLT,
                PushRetryDlqReplayMeasurementTest.CHAT_TOPIC,
                PushRetryDlqReplayMeasurementTest.CHAT_TOPIC + ".retry-0",
                PushRetryDlqReplayMeasurementTest.CHAT_TOPIC + ".retry-1",
                PushRetryDlqReplayMeasurementTest.CHAT_TOPIC + ".retry-2",
                PushRetryDlqReplayMeasurementTest.CHAT_TOPIC + ".retry-3",
                PushRetryDlqReplayMeasurementTest.CHAT_TOPIC + ".dlq"
        },
        brokerProperties = "auto.create.topics.enable=false"
)
class PushRetryDlqReplayMeasurementTest {

    static final String TOPIC = "meetple.push.notification.v1";
    static final String DLT = TOPIC + ".dlq";
    static final String CHAT_TOPIC = "meetple.push.chat.v1";
    private static final int EVENT_COUNT = 100;
    private static final PushDeviceTarget TARGET = new PushDeviceTarget(1L, "measurement-token");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private RecordingPushMessageSender sender;

    @MockitoBean
    private PushDeviceTokenService pushDeviceTokenService;

    @MockitoBean
    private PushDeliveryService pushDeliveryService;

    @MockitoBean
    private ChatNotificationSettingService chatNotificationSettingService;

    @BeforeEach
    void setUp() {
        sender.reset();
        given(pushDeviceTokenService.findTargets(any())).willReturn(List.of(TARGET));
        given(pushDeliveryService.prepare(any(), any())).willAnswer(invocation ->
                new PushDeliveryClaim(UUID.randomUUID(), List.of(TARGET), false)
        );
    }

    @Test
    void movesOneHundredEventsToDltAndReplaysEveryEventOnce() throws Exception {
        String runId = UUID.randomUUID().toString();
        Map<String, UUID> eventIdsByKey = new HashMap<>();
        Instant failureStartedAt = Instant.now();

        try (Consumer<String, String> consumer = dltConsumer()) {
            for (int index = 0; index < EVENT_COUNT; index++) {
                UUID eventId = UUID.randomUUID();
                String key = runId + "-" + index;
                eventIdsByKey.put(key, eventId);
                kafkaTemplate.send(TOPIC, key, payload(eventId, runId, index)).get(10, SECONDS);
            }

            List<ConsumerRecord<String, String>> dltRecords = awaitDltRecords(
                    consumer,
                    eventIdsByKey.keySet(),
                    Duration.ofSeconds(60)
            );
            Instant dltCompletedAt = Instant.now();

            assertThat(dltRecords).hasSize(EVENT_COUNT);
            assertThat(sender.attemptCounts()).hasSize(EVENT_COUNT);
            assertThat(sender.attemptCounts().values()).allMatch(attempts -> attempts.size() == 5);

            sender.succeed();
            Instant replayStartedAt = Instant.now();
            for (ConsumerRecord<String, String> record : dltRecords) {
                kafkaTemplate.send(TOPIC, record.key(), record.value()).get(10, SECONDS);
            }

            awaitSuccesses(eventIdsByKey.values(), Duration.ofSeconds(30));
            Instant replayCompletedAt = Instant.now();

            assertThat(sender.successCounts()).hasSize(EVENT_COUNT);
            assertThat(sender.successCounts().values()).containsOnly(1);

            System.out.printf(
                    "PUSH_RETRY_MEASUREMENT runId=%s events=%d failedAttempts=%d "
                            + "dltDurationMs=%d replayDurationMs=%d successes=%d duplicateSuccesses=%d%n",
                    runId,
                    EVENT_COUNT,
                    sender.totalAttemptsBeforeRecovery(),
                    Duration.between(failureStartedAt, dltCompletedAt).toMillis(),
                    Duration.between(replayStartedAt, replayCompletedAt).toMillis(),
                    sender.successCounts().size(),
                    sender.duplicateSuccesses()
            );
        }
    }

    private Consumer<String, String> dltConsumer() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                embeddedKafka,
                "dlt-measurement-" + UUID.randomUUID(),
                false
        );
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, DLT);
        return consumer;
    }

    private List<ConsumerRecord<String, String>> awaitDltRecords(
            Consumer<String, String> consumer,
            Set<String> expectedKeys,
            Duration timeout
    ) {
        Map<String, ConsumerRecord<String, String>> recordsByKey = new HashMap<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && recordsByKey.size() < expectedKeys.size()) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedKeys.contains(record.key())) {
                    recordsByKey.putIfAbsent(record.key(), record);
                }
            }
        }
        return new ArrayList<>(recordsByKey.values());
    }

    private void awaitSuccesses(Iterable<UUID> eventIds, Duration timeout) throws InterruptedException {
        Set<UUID> expectedIds = new HashSet<>();
        eventIds.forEach(expectedIds::add);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && !sender.successCounts().keySet().containsAll(expectedIds)) {
            Thread.sleep(50);
        }
        assertThat(sender.successCounts().keySet()).containsAll(expectedIds);
    }

    private String payload(UUID eventId, String runId, int index) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "PUSH_RETRY_MEASUREMENT",
                  "schemaVersion": 1,
                  "occurredAt": "%s",
                  "aggregateType": "push-retry-measurement",
                  "aggregateId": "%s",
                  "data": {
                    "recipientMemberId": 1,
                    "title": "retry measurement",
                    "body": "event %d"
                  }
                }
                """.formatted(eventId, Instant.now(), runId, index);
    }

    @TestConfiguration
    static class SenderConfiguration {

        @Bean
        @Primary
        RecordingPushMessageSender recordingPushMessageSender() {
            return new RecordingPushMessageSender();
        }
    }

    static class RecordingPushMessageSender implements PushMessageSender {

        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.FAIL);
        private final Map<UUID, List<Instant>> attemptCounts = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> successCounts = new ConcurrentHashMap<>();

        @Override
        public PushSendResult send(PushMessage message, List<PushDeviceTarget> targets) {
            UUID eventId = UUID.fromString(message.data().get("eventId"));
            attemptCounts.computeIfAbsent(eventId, ignored -> new CopyOnWriteArrayList<>()).add(Instant.now());
            if (mode.get() == Mode.FAIL) {
                return new PushSendResult(
                        List.of(),
                        List.of(),
                        List.of(new PushSendFailure(TARGET.deviceTokenId(), "UNAVAILABLE"))
                );
            }
            successCounts.merge(eventId, 1, Integer::sum);
            return new PushSendResult(List.of(TARGET.deviceTokenId()), List.of(), List.of());
        }

        void succeed() {
            mode.set(Mode.SUCCESS);
        }

        void reset() {
            mode.set(Mode.FAIL);
            attemptCounts.clear();
            successCounts.clear();
        }

        Map<UUID, List<Instant>> attemptCounts() {
            return attemptCounts;
        }

        Map<UUID, Integer> successCounts() {
            return successCounts;
        }

        int totalAttemptsBeforeRecovery() {
            return attemptCounts.values().stream().mapToInt(List::size).sum() - successCounts.size();
        }

        int duplicateSuccesses() {
            return successCounts.values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
        }

        private enum Mode {
            FAIL,
            SUCCESS
        }
    }
}
