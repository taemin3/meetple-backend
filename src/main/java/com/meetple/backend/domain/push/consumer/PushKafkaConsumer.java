package com.meetple.backend.domain.push.consumer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "push.kafka", name = "consumer-enabled", havingValue = "true")
public class PushKafkaConsumer {

    private final PushEventProcessor pushEventProcessor;

    @RetryableTopic(
            attempts = "5",
            backOff = @BackOff(
                    delayString = "${push.kafka.retry.initial-delay-ms:1000}",
                    multiplierString = "${push.kafka.retry.multiplier:10.0}",
                    maxDelayString = "${push.kafka.retry.max-delay-ms:300000}"
            ),
            kafkaTemplate = "kafkaTemplate",
            autoCreateTopics = "false",
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".dlq",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = NonRetryablePushEventException.class,
            traversingCauses = "true"
    )
    @KafkaListener(
            topics = {"meetple.push.notification.v1", "meetple.push.chat.v1"},
            groupId = "${PUSH_KAFKA_CONSUMER_GROUP:meetple-push-fcm-v1}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        pushEventProcessor.process(originalTopic(record), record.value());
    }

    @DltHandler
    public void consumeDlt(ConsumerRecord<String, String> record) {
        Headers headers = record.headers();
        log.error(
                "Push event moved to DLQ: dlqTopic={}, dlqPartition={}, dlqOffset={}, "
                        + "originalTopic={}, originalPartition={}, originalOffset={}, "
                        + "exceptionType={}, exceptionCauseType={}, exceptionMessage={}",
                record.topic(),
                record.partition(),
                record.offset(),
                textHeader(headers, KafkaHeaders.ORIGINAL_TOPIC),
                intHeader(headers, KafkaHeaders.ORIGINAL_PARTITION),
                longHeader(headers, KafkaHeaders.ORIGINAL_OFFSET),
                textHeader(headers, KafkaHeaders.EXCEPTION_FQCN),
                textHeader(headers, KafkaHeaders.EXCEPTION_CAUSE_FQCN),
                textHeader(headers, KafkaHeaders.EXCEPTION_MESSAGE)
        );
    }

    private String originalTopic(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.ORIGINAL_TOPIC);
        if (header == null || header.value() == null) {
            return record.topic();
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private String textHeader(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        return header == null || header.value() == null
                ? "unknown"
                : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String intHeader(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            return "unknown";
        }
        byte[] value = header.value();
        if (value.length == Integer.BYTES) {
            return Integer.toString(ByteBuffer.wrap(value).getInt());
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    private String longHeader(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        if (header == null || header.value() == null) {
            return "unknown";
        }
        byte[] value = header.value();
        if (value.length == Long.BYTES) {
            return Long.toString(ByteBuffer.wrap(value).getLong());
        }
        return new String(value, StandardCharsets.UTF_8);
    }
}
