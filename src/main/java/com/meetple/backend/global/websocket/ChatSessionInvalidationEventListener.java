package com.meetple.backend.global.websocket;

import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatSessionInvalidationEventListener {

    private final ChatSessionInvalidationService invalidationService;
    private final ChatSessionInvalidationRedisPublisher redisPublisher;
    private final RefreshTokenRepository refreshTokenRepository;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void handle(ChatSessionInvalidationEvent event) {
        if (event.reason() == ChatAccessRevocationReason.MEMBER_SUSPENDED) {
            refreshTokenRepository.deleteAllByMemberId(event.memberId());
        }
        ChatSessionInvalidationEvent committedEvent = event.withOccurredAt(
                Instant.now()
        );
        invalidationService.invalidateLocalSessions(committedEvent);
        redisPublisher.publish(committedEvent);
    }
}
