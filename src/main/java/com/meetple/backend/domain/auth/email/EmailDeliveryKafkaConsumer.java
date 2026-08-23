package com.meetple.backend.domain.auth.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "auth.email-delivery.kafka",
        name = "consumer-enabled",
        havingValue = "true"
)
public class EmailDeliveryKafkaConsumer {

    private final EmailDeliveryEventProcessor eventProcessor;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(
                    delayString = "${auth.email-delivery.kafka.retry.initial-delay-ms:5000}",
                    multiplierString = "${auth.email-delivery.kafka.retry.multiplier:6.0}",
                    maxDelayString = "${auth.email-delivery.kafka.retry.max-delay-ms:120000}"
            ),
            kafkaTemplate = "kafkaTemplate",
            autoCreateTopics = "false",
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlq",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = NonRetryableEmailDeliveryException.class,
            traversingCauses = "true"
    )
    @KafkaListener(
            topics = "meetple.email.delivery.v1",
            groupId = "${EMAIL_DELIVERY_KAFKA_CONSUMER_GROUP:meetple-email-delivery-v1}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        eventProcessor.process(record.value());
    }

    @DltHandler
    public void consumeDlt(ConsumerRecord<String, String> record) {
        try {
            eventProcessor.discard(record.value());
        } catch (RuntimeException cleanupException) {
            log.error(
                    "Failed to clean up email delivery after DLQ: topic={}, partition={}, offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    cleanupException
            );
        }
        log.error(
                "Email delivery event moved to DLQ: topic={}, partition={}, offset={}, key={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key()
        );
    }
}
