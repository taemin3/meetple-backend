package com.meetple.backend.domain.push.config;

import com.meetple.backend.domain.push.consumer.PushEventProcessingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "push.kafka", name = "consumer-enabled", havingValue = "true")
public class PushKafkaConsumerConfig {

    @Bean
    public CommonErrorHandler pushKafkaErrorHandler() {
        return new DefaultErrorHandler(
                (record, exception) -> {
                    throw new PushEventProcessingException(
                            "Push event retries exhausted at "
                                    + record.topic() + "-" + record.partition() + "@" + record.offset(),
                            exception
                    );
                },
                new FixedBackOff(1_000L, 2L)
        );
    }
}
