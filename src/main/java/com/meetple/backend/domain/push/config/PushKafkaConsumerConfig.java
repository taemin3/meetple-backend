package com.meetple.backend.domain.push.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
@EnableKafkaRetryTopic
@ConditionalOnProperty(prefix = "push.kafka", name = "consumer-enabled", havingValue = "true")
public class PushKafkaConsumerConfig {

    @Bean(name = "taskScheduler")
    public TaskScheduler applicationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("application-scheduling-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    @Bean
    public RetryTopicSchedulerWrapper pushKafkaRetrySchedulerWrapper() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("push-kafka-retry-");
        scheduler.setDaemon(true);
        return new RetryTopicSchedulerWrapper(scheduler);
    }
}
