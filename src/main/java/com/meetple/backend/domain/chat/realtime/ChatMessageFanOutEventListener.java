package com.meetple.backend.domain.chat.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageFanOutEventListener {

    private final ChatMessageFanOutService fanOutService;
    private final ChatMessageFanOutRedisPublisher redisPublisher;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void handle(ChatMessageFanOutEvent event) {
        fanOutService.fanOutToLocalSubscribers(event);
        redisPublisher.publish(event);
    }
}
