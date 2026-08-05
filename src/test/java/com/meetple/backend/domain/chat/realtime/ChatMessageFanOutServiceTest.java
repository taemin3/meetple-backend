package com.meetple.backend.domain.chat.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import com.meetple.backend.global.response.ApiResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ChatMessageFanOutServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void fansOutSameEventOnlyOnceWithExistingWebSocketEnvelope() {
        ChatMessageFanOutService service = new ChatMessageFanOutService(messagingTemplate);
        ChatMessageFanOutEvent event = event();

        service.fanOutToLocalSubscribers(event);
        service.fanOutToLocalSubscribers(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/chat/rooms/10"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).isInstanceOf(ApiResponse.class);
        ApiResponse<?> response = (ApiResponse<?>) payloadCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo(20000);
        assertThat(response.getData()).isEqualTo(event.message());
    }

    @Test
    void failedLocalFanOutCanBeRetriedByRedisEcho() {
        ChatMessageFanOutService service = new ChatMessageFanOutService(messagingTemplate);
        ChatMessageFanOutEvent event = event();
        doThrow(new IllegalStateException("temporary failure"))
                .doNothing()
                .when(messagingTemplate)
                .convertAndSend(
                        eq("/topic/chat/rooms/10"),
                        org.mockito.ArgumentMatchers.any(ApiResponse.class)
                );

        service.fanOutToLocalSubscribers(event);
        service.fanOutToLocalSubscribers(event);

        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/chat/rooms/10"),
                org.mockito.ArgumentMatchers.any(ApiResponse.class)
        );
    }

    private ChatMessageFanOutEvent event() {
        UUID clientMessageId = UUID.randomUUID();
        ChatMessageResponse message = new ChatMessageResponse(
                100L,
                10L,
                7L,
                clientMessageId,
                1L,
                "member",
                null,
                "hello",
                LocalDateTime.of(2026, 8, 6, 0, 0)
        );
        return new ChatMessageFanOutEvent(UUID.randomUUID(), message);
    }
}
