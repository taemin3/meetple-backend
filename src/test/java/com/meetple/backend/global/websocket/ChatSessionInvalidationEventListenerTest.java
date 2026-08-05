package com.meetple.backend.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.same;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSessionInvalidationEventListenerTest {

    @Mock
    private ChatSessionInvalidationService invalidationService;

    @Mock
    private ChatSessionInvalidationRedisPublisher redisPublisher;

    @InjectMocks
    private ChatSessionInvalidationEventListener listener;

    @Test
    void usesSamePostCommitEventForLocalInvalidationAndRedisPublishing() {
        ChatSessionInvalidationEvent event =
                ChatSessionInvalidationEvent.meetingCanceled(10L)
                        .withOccurredAt(Instant.EPOCH);

        listener.handle(event);

        InOrder inOrder = inOrder(invalidationService, redisPublisher);
        ArgumentCaptor<ChatSessionInvalidationEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatSessionInvalidationEvent.class);
        inOrder.verify(invalidationService).invalidateLocalSessions(
                eventCaptor.capture()
        );
        ChatSessionInvalidationEvent committedEvent = eventCaptor.getValue();
        inOrder.verify(redisPublisher).publish(same(committedEvent));

        assertThat(committedEvent.eventId()).isEqualTo(event.eventId());
        assertThat(committedEvent.target()).isEqualTo(event.target());
        assertThat(committedEvent.reason()).isEqualTo(event.reason());
        assertThat(committedEvent.occurredAt()).isAfter(event.occurredAt());
    }
}
