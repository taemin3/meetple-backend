package com.meetple.backend.global.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatSessionInvalidationEventListener {

    private final ChatSessionInvalidationService invalidationService;
    private final ChatSessionInvalidationRedisPublisher redisPublisher;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void handle(ChatSessionInvalidationEvent event) {
        invalidationService.invalidateLocalSessions(event);
        redisPublisher.publish(event);
    }
}
