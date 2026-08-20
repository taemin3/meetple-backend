package com.meetple.backend.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableKafkaRetryTopic
@ConditionalOnExpression(
        "${push.kafka.consumer-enabled:false} || ${image.deletion.kafka.consumer-enabled:false}"
)
public class KafkaConsumerConfig {

    @Bean(name = "taskScheduler")
    public TaskScheduler applicationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("application-scheduling-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    @Bean
    public RetryTopicSchedulerWrapper kafkaRetrySchedulerWrapper() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.setDaemon(true);
        return new RetryTopicSchedulerWrapper(scheduler);
    }
}
