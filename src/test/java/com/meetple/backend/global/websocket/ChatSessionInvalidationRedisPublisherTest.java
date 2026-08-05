package com.meetple.backend.global.websocket;

import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class ChatSessionInvalidationRedisPublisherTest {

    @Test
    void redisDisabledSkipsSerializationAndPublishing() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(
                StringRedisTemplate.class
        );
        ObjectMapper objectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        ChatSessionInvalidationRedisPublisher publisher =
                new ChatSessionInvalidationRedisPublisher(
                        redisTemplate,
                        objectMapper,
                        false
                );

        publisher.publish(ChatSessionInvalidationEvent.meetingCanceled(10L));

        verifyNoInteractions(redisTemplate, objectMapper);
    }
}
