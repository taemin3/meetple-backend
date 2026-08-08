package com.meetple.backend.domain.push.consumer;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "push.kafka", name = "consumer-enabled", havingValue = "true")
public class PushKafkaConsumer {

    private final PushEventProcessor pushEventProcessor;

    @KafkaListener(
            topics = {"meetple.push.notification.v1", "meetple.push.chat.v1"},
            groupId = "${PUSH_KAFKA_CONSUMER_GROUP:meetple-push-fcm-v1}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        pushEventProcessor.process(record.topic(), record.value());
    }
}
