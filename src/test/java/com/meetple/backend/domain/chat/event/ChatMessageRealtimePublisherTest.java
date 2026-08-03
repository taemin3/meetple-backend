package com.meetple.backend.domain.chat.event;

import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatMessageRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatMessageRealtimePublisher publisher;

    @Test
    void publishesCommittedMessageToRoomTopic() {
        ChatMessageResponse message = new ChatMessageResponse(
                100L,
                10L,
                7L,
                UUID.randomUUID(),
                1L,
                "member",
                null,
                "hello",
                LocalDateTime.of(2026, 8, 4, 0, 0)
        );

        publisher.publish(new ChatMessageCreatedEvent(message));

        verify(messagingTemplate).convertAndSend("/topic/chat/rooms/10", message);
    }
}
