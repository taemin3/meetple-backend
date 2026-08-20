package com.meetple.backend.domain.image.consumer;

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
@ConditionalOnProperty(prefix = "image.deletion.kafka", name = "consumer-enabled", havingValue = "true")
public class ImageDeletionKafkaConsumer {

    private final ImageDeletionEventProcessor eventProcessor;

    @RetryableTopic(
            attempts = "5",
            backOff = @BackOff(
                    delayString = "${image.deletion.kafka.retry.initial-delay-ms:60000}",
                    multiplierString = "${image.deletion.kafka.retry.multiplier:5.0}",
                    maxDelayString = "${image.deletion.kafka.retry.max-delay-ms:3600000}"
            ),
            kafkaTemplate = "kafkaTemplate",
            autoCreateTopics = "false",
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlq",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = NonRetryableImageDeletionEventException.class,
            traversingCauses = "true"
    )
    @KafkaListener(
            topics = "meetple.image.delete.v1",
            groupId = "${IMAGE_DELETION_KAFKA_CONSUMER_GROUP:meetple-image-deletion-v1}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        eventProcessor.process(record.value());
    }

    @DltHandler
    public void consumeDlt(ConsumerRecord<String, String> record) {
        log.error(
                "Image deletion event moved to DLQ: topic={}, partition={}, offset={}, key={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key()
        );
    }
}
