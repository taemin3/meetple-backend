package com.meetple.backend.domain.chat.realtime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;

@ExtendWith(MockitoExtension.class)
class ChatMessageFanOutRedisSubscriberTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ChatMessageFanOutService fanOutService;

    @Mock
    private Message message;

    @InjectMocks
    private ChatMessageFanOutRedisSubscriber subscriber;

    @Test
    void deserializesRedisEventAndFansOutToLocalSubscribers() throws Exception {
        ChatMessageFanOutEvent event = event();
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        when(message.getBody()).thenReturn(body);
        when(objectMapper.readValue("payload", ChatMessageFanOutEvent.class))
                .thenReturn(event);

        subscriber.onMessage(message, null);

        verify(fanOutService).fanOutToLocalSubscribers(event);
    }

    private ChatMessageFanOutEvent event() {
        ChatMessageResponse chatMessage = new ChatMessageResponse(
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
        return new ChatMessageFanOutEvent(UUID.randomUUID(), chatMessage);
    }
}
