package com.meetple.backend.domain.chat.realtime;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class ChatMessageFanOutRedisPublisherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void publishesSerializedEventToDedicatedChannel() throws JsonProcessingException {
        ChatMessageFanOutEvent event = event();
        when(objectMapper.writeValueAsString(event)).thenReturn("payload");
        ChatMessageFanOutRedisPublisher publisher =
                new ChatMessageFanOutRedisPublisher(redisTemplate, objectMapper, true);

        publisher.publish(event);

        verify(redisTemplate).convertAndSend(
                ChatMessageFanOutRedisPublisher.CHANNEL,
                "payload"
        );
    }

    @Test
    void doesNothingWhenRedisFanOutIsDisabled() throws JsonProcessingException {
        ChatMessageFanOutEvent event = event();
        ChatMessageFanOutRedisPublisher publisher =
                new ChatMessageFanOutRedisPublisher(redisTemplate, objectMapper, false);

        publisher.publish(event);

        verify(objectMapper, never()).writeValueAsString(event);
        verify(redisTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private ChatMessageFanOutEvent event() {
        ChatMessageResponse message = new ChatMessageResponse(
                100L,
                10L,
                7L,
                UUID.randomUUID(),
                1L,
                "member",
                null,
                "hello",
                LocalDateTime.of(2026, 8, 6, 0, 0)
        );
        return new ChatMessageFanOutEvent(UUID.randomUUID(), message);
    }
}
