package com.meetple.backend.domain.image.consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "push.kafka.consumer-enabled=false",
        "image.deletion.kafka.consumer-enabled=true",
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:meetple-image-deletion-retry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "image.deletion.kafka.retry.initial-delay-ms=50",
        "image.deletion.kafka.retry.multiplier=2.0",
        "image.deletion.kafka.retry.max-delay-ms=200",
        "IMAGE_DELETION_KAFKA_CONSUMER_GROUP=meetple-image-deletion-retry-integration"
})
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {
                ImageDeletionKafkaRetryIntegrationTest.TOPIC,
                ImageDeletionKafkaRetryIntegrationTest.TOPIC + ".retry-0",
                ImageDeletionKafkaRetryIntegrationTest.TOPIC + ".retry-1",
                ImageDeletionKafkaRetryIntegrationTest.TOPIC + ".retry-2",
                ImageDeletionKafkaRetryIntegrationTest.TOPIC + ".retry-3",
                ImageDeletionKafkaRetryIntegrationTest.DLT
        },
        brokerProperties = "auto.create.topics.enable=false"
)
class ImageDeletionKafkaRetryIntegrationTest {

    static final String TOPIC = "meetple.image.delete.v1";
    static final String DLT = TOPIC + ".dlq";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private ImageDeletionEventProcessor eventProcessor;

    @BeforeEach
    void setUp() {
        reset(eventProcessor);
    }

    @Test
    void retriesTransientFailureUntilDeletionSucceeds() throws Exception {
        String key = "retry-" + UUID.randomUUID();
        String payload = "payload-" + key;
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary S3 failure");
            }
            return null;
        }).when(eventProcessor).process(eq(payload));

        kafkaTemplate.send(TOPIC, key, payload).get(10, SECONDS);

        verify(eventProcessor, timeout(10_000).times(3)).process(payload);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void sendsInvalidEventDirectlyToDlt() throws Exception {
        String key = "invalid-" + UUID.randomUUID();
        String payload = "invalid-payload-" + key;
        doThrow(new NonRetryableImageDeletionEventException("invalid contract"))
                .when(eventProcessor).process(payload);

        try (Consumer<String, String> consumer = dltConsumer()) {
            kafkaTemplate.send(TOPIC, key, payload).get(10, SECONDS);
            ConsumerRecord<String, String> dltRecord = awaitRecord(consumer, key);

            assertThat(dltRecord.value()).isEqualTo(payload);
        }

        verify(eventProcessor, timeout(5_000).times(1)).process(payload);
    }

    private Consumer<String, String> dltConsumer() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                embeddedKafka,
                "image-deletion-dlt-probe-" + UUID.randomUUID(),
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
}
