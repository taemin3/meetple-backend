package com.meetple.backend.domain.ai;

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
        prefix = "ai.embedding.kafka",
        name = "consumer-enabled",
        havingValue = "true"
)
public class MeetingEmbeddingKafkaConsumer {
    private final MeetingEmbeddingEventProcessor eventProcessor;

    @RetryableTopic(
            attempts = "5",
            backOff = @BackOff(
                    delayString = "${ai.embedding.kafka.retry.initial-delay-ms:10000}",
                    multiplierString = "${ai.embedding.kafka.retry.multiplier:3.0}",
                    maxDelayString = "${ai.embedding.kafka.retry.max-delay-ms:600000}"
            ),
            kafkaTemplate = "kafkaTemplate",
            autoCreateTopics = "false",
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlq",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = NonRetryableMeetingEmbeddingException.class,
            traversingCauses = "true"
    )
    @KafkaListener(
            topics = "meetple.ai.meeting-embedding.v1",
            groupId = "${AI_EMBEDDING_KAFKA_CONSUMER_GROUP:meetple-ai-meeting-embedding-v1}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        eventProcessor.process(record.value());
    }

    @DltHandler
    public void consumeDlt(ConsumerRecord<String, String> record) {
        log.error(
                "Meeting embedding event moved to DLQ: topic={}, partition={}, offset={}, key={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key()
        );
    }
}
