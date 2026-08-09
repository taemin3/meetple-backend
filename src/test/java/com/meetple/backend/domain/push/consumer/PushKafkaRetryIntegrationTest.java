package com.meetple.backend.domain.push.consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "push.kafka.consumer-enabled=true",
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:meetple-push-retry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "push.kafka.retry.initial-delay-ms=50",
        "push.kafka.retry.multiplier=2.0",
        "push.kafka.retry.max-delay-ms=200",
        "PUSH_KAFKA_CONSUMER_GROUP=meetple-push-retry-integration"
})
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {
                PushKafkaRetryIntegrationTest.NOTIFICATION_TOPIC,
                PushKafkaRetryIntegrationTest.CHAT_TOPIC,
                PushKafkaRetryIntegrationTest.NOTIFICATION_TOPIC + ".retry-0",
                PushKafkaRetryIntegrationTest.NOTIFICATION_TOPIC + ".retry-1",
                PushKafkaRetryIntegrationTest.NOTIFICATION_TOPIC + ".retry-2",
                PushKafkaRetryIntegrationTest.NOTIFICATION_TOPIC + ".retry-3",
                PushKafkaRetryIntegrationTest.NOTIFICATION_DLT,
                PushKafkaRetryIntegrationTest.CHAT_TOPIC + ".retry-0",
                PushKafkaRetryIntegrationTest.CHAT_TOPIC + ".retry-1",
                PushKafkaRetryIntegrationTest.CHAT_TOPIC + ".retry-2",
                PushKafkaRetryIntegrationTest.CHAT_TOPIC + ".retry-3",
                PushKafkaRetryIntegrationTest.CHAT_TOPIC + ".dlq"
        },
        brokerProperties = "auto.create.topics.enable=false"
)
class PushKafkaRetryIntegrationTest {

    static final String NOTIFICATION_TOPIC = "meetple.push.notification.v1";
    static final String CHAT_TOPIC = "meetple.push.chat.v1";
    static final String NOTIFICATION_DLT = NOTIFICATION_TOPIC + ".dlq";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RetryTopicSchedulerWrapper retryTopicSchedulerWrapper;

    @MockitoBean
    private PushEventProcessor pushEventProcessor;

    @BeforeEach
    void setUp() {
        reset(pushEventProcessor);
    }

    @Test
    void retriesTransientFailureThroughRetryTopicsUntilProcessingSucceeds() throws Exception {
        String key = "retry-" + UUID.randomUUID();
        String payload = "transient-payload-" + key;
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new PushEventProcessingException("temporary failure");
            }
            return null;
        }).when(pushEventProcessor).process(eq(NOTIFICATION_TOPIC), eq(payload));

        kafkaTemplate.send(NOTIFICATION_TOPIC, key, payload).get(10, SECONDS);

        verify(pushEventProcessor, timeout(10_000).times(3))
                .process(NOTIFICATION_TOPIC, payload);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void sendsTransientFailureToDltAfterAllRetryTopicsAreExhausted() throws Exception {
        String key = "retry-dlt-" + UUID.randomUUID();
        String payload = "transient-dlt-payload-" + key;
        doThrow(new PushEventProcessingException("temporary failure"))
                .when(pushEventProcessor)
                .process(NOTIFICATION_TOPIC, payload);

        try (Consumer<String, String> consumer = dltConsumer()) {
            kafkaTemplate.send(NOTIFICATION_TOPIC, key, payload).get(10, SECONDS);
            ConsumerRecord<String, String> dltRecord = awaitRecord(consumer, key);

            assertThat(dltRecord.value()).isEqualTo(payload);
        }

        verify(pushEventProcessor, timeout(10_000).times(5))
                .process(NOTIFICATION_TOPIC, payload);
    }

    @Test
    void keepsKafkaRetrySchedulerOutOfApplicationTaskSchedulers() {
        TaskScheduler applicationTaskScheduler = applicationContext.getBean(
                "taskScheduler",
                TaskScheduler.class
        );

        assertThat(applicationContext.getBeansOfType(TaskScheduler.class).values())
                .doesNotContain(retryTopicSchedulerWrapper.getScheduler());
        assertThat(applicationTaskScheduler)
                .isNotSameAs(retryTopicSchedulerWrapper.getScheduler())
                .isInstanceOfSatisfying(
                        ThreadPoolTaskScheduler.class,
                        scheduler -> assertThat(scheduler.getThreadNamePrefix())
                                .isEqualTo("application-scheduling-")
                );
        assertThat(retryTopicSchedulerWrapper.getScheduler())
                .isInstanceOfSatisfying(
                        ThreadPoolTaskScheduler.class,
                        scheduler -> assertThat(scheduler.getThreadNamePrefix())
                                .isEqualTo("push-kafka-retry-")
                );
    }

    @Test
    void sendsPermanentFailureDirectlyToDltWithOriginalRecordHeaders() throws Exception {
        String key = "dlt-" + UUID.randomUUID();
        String payload = "permanent-payload-" + key;
        doThrow(new NonRetryablePushEventException("invalid contract"))
                .when(pushEventProcessor)
                .process(NOTIFICATION_TOPIC, payload);

        try (Consumer<String, String> consumer = dltConsumer()) {
            kafkaTemplate.send(NOTIFICATION_TOPIC, key, payload).get(10, SECONDS);
            ConsumerRecord<String, String> dltRecord = awaitRecord(consumer, key);

            assertThat(dltRecord.value()).isEqualTo(payload);
            assertThat(textHeader(dltRecord, KafkaHeaders.ORIGINAL_TOPIC))
                    .isEqualTo(NOTIFICATION_TOPIC);
            assertThat(intHeader(dltRecord, KafkaHeaders.ORIGINAL_PARTITION)).isZero();
            assertThat(longHeader(dltRecord, KafkaHeaders.ORIGINAL_OFFSET)).isNotNegative();
            assertThat(textHeader(dltRecord, KafkaHeaders.EXCEPTION_CAUSE_FQCN))
                    .contains(NonRetryablePushEventException.class.getSimpleName());
        }

        verify(pushEventProcessor, timeout(5_000).times(1))
                .process(NOTIFICATION_TOPIC, payload);
    }

    private Consumer<String, String> dltConsumer() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                embeddedKafka,
                "dlt-probe-" + UUID.randomUUID(),
                false
        );
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                properties,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, NOTIFICATION_DLT);
        return consumer;
    }

    private ConsumerRecord<String, String> awaitRecord(Consumer<String, String> consumer, String key) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
            for (ConsumerRecord<String, String> record : records) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("DLT record was not received for key " + key);
    }

    private String textHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private int intHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).isNotNull();
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private long longHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        assertThat(header).isNotNull();
        return ByteBuffer.wrap(header.value()).getLong();
    }
}
