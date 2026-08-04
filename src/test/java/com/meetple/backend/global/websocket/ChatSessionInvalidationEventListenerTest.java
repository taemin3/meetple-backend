package com.meetple.backend.global.websocket;

import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    void invalidatesLocalSessionsBeforePublishingToRedis() {
        ChatSessionInvalidationEvent event =
                ChatSessionInvalidationEvent.meetingCanceled(10L);

        listener.handle(event);

        InOrder inOrder = inOrder(invalidationService, redisPublisher);
        inOrder.verify(invalidationService).invalidateLocalSessions(event);
        inOrder.verify(redisPublisher).publish(event);
    }
}
