package com.meetple.backend.domain.chat.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageRealtimePublisher {

    private static final String ROOM_TOPIC_PREFIX = "/topic/chat/rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ChatMessageCreatedEvent event) {
        messagingTemplate.convertAndSend(
                ROOM_TOPIC_PREFIX + event.message().roomId(),
                event.message()
        );
    }
}
