package com.meetple.backend.domain.auth.email;

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
        "image.deletion.kafka.consumer-enabled=false",
        "auth.email-delivery.kafka.consumer-enabled=true",
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:meetple-email-delivery-retry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "auth.email-delivery.kafka.retry.initial-delay-ms=50",
        "auth.email-delivery.kafka.retry.multiplier=2.0",
        "auth.email-delivery.kafka.retry.max-delay-ms=200",
        "EMAIL_DELIVERY_KAFKA_CONSUMER_GROUP=meetple-email-delivery-retry-integration"
})
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {
                EmailDeliveryKafkaRetryIntegrationTest.TOPIC,
                EmailDeliveryKafkaRetryIntegrationTest.TOPIC + ".retry-0",
                EmailDeliveryKafkaRetryIntegrationTest.TOPIC + ".retry-1",
                EmailDeliveryKafkaRetryIntegrationTest.TOPIC + ".retry-2",
                EmailDeliveryKafkaRetryIntegrationTest.DLT
        },
        brokerProperties = "auto.create.topics.enable=false"
)
class EmailDeliveryKafkaRetryIntegrationTest {

    static final String TOPIC = "meetple.email.delivery.v1";
    static final String DLT = TOPIC + ".dlq";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private EmailDeliveryEventProcessor eventProcessor;

    @BeforeEach
    void setUp() {
        reset(eventProcessor);
    }

    @Test
    void retriesTransientSmtpFailureUntilDeliverySucceeds() throws Exception {
        String key = "retry-" + UUID.randomUUID();
        String payload = "payload-" + key;
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                throw new EmailDeliveryProcessingException("temporary SES failure");
            }
            return null;
        }).when(eventProcessor).process(eq(payload));

        kafkaTemplate.send(TOPIC, key, payload).get(10, SECONDS);

        verify(eventProcessor, timeout(10_000).times(3)).process(payload);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void sendsInvalidEventToDltAndRunsTerminalCleanup() throws Exception {
        String key = "invalid-" + UUID.randomUUID();
        String payload = "invalid-payload-" + key;
        doThrow(new NonRetryableEmailDeliveryException("invalid contract"))
                .when(eventProcessor).process(payload);

        try (Consumer<String, String> consumer = dltConsumer()) {
            kafkaTemplate.send(TOPIC, key, payload).get(10, SECONDS);
            ConsumerRecord<String, String> dltRecord = awaitRecord(consumer, key);

            assertThat(dltRecord.value()).isEqualTo(payload);
        }

        verify(eventProcessor, timeout(5_000)).discard(payload);
        verify(eventProcessor, timeout(5_000).times(1)).process(payload);
    }

    private Consumer<String, String> dltConsumer() {
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                embeddedKafka,
                "email-delivery-dlt-probe-" + UUID.randomUUID(),
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
