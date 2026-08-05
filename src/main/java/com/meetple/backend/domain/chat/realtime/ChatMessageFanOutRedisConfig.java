package com.meetple.backend.domain.chat.realtime;

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
        name = "chat.message-fan-out.redis-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ChatMessageFanOutRedisConfig {

    private final ChatMessageFanOutRedisSubscriber subscriber;

    @Bean
    public RedisMessageListenerContainer chatMessageFanOutListenerContainer(
            RedisConnectionFactory connectionFactory
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                subscriber,
                new ChannelTopic(ChatMessageFanOutRedisPublisher.CHANNEL)
        );
        return container;
    }
}
