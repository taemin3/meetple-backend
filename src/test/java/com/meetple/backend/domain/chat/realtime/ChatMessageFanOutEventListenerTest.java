package com.meetple.backend.domain.chat.realtime;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import com.meetple.backend.global.performance.ChatRealtimeMeasurementRecorder;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageFanOutEventListenerTest {

    @Mock
    private ChatMessageFanOutService fanOutService;

    @Mock
    private ChatMessageFanOutRedisPublisher redisPublisher;

    @Mock
    private ChatRealtimeMeasurementRecorder measurementRecorder;

    @InjectMocks
    private ChatMessageFanOutEventListener listener;

    @Test
    void fansOutLocallyBeforePublishingSameEventToRedis() {
        ChatMessageFanOutEvent event = event();

        listener.handle(event);

        InOrder inOrder = inOrder(fanOutService, redisPublisher);
        inOrder.verify(fanOutService).fanOutToLocalSubscribers(event);
        inOrder.verify(redisPublisher).publish(event);
        verify(measurementRecorder).start(
                event.message().clientMessageId(),
                ChatRealtimeMeasurementRecorder.LOCAL_FAN_OUT
        );
        verify(measurementRecorder).start(
                event.message().clientMessageId(),
                ChatRealtimeMeasurementRecorder.REDIS_PUBLISH
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
