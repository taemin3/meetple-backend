package com.meetple.backend.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "chat.session-invalidation.redis-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ChatSessionInvalidationRedisConfig {

    private final ChatSessionInvalidationRedisSubscriber subscriber;

    @Bean
    public RedisMessageListenerContainer chatSessionInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                subscriber,
                new ChannelTopic(ChatSessionInvalidationRedisPublisher.CHANNEL)
        );
        return container;
    }
}
