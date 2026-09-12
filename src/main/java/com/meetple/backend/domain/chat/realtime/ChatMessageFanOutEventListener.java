package com.meetple.backend.domain.chat.realtime;

import com.meetple.backend.global.performance.ChatRealtimeMeasurementRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageFanOutEventListener {

    private final ChatMessageFanOutService fanOutService;
    private final ChatMessageFanOutRedisPublisher redisPublisher;
    private final ChatRealtimeMeasurementRecorder measurementRecorder;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void handle(ChatMessageFanOutEvent event) {
        try (ChatRealtimeMeasurementRecorder.Timer ignored = measurementRecorder.start(
                event.message().clientMessageId(),
                ChatRealtimeMeasurementRecorder.LOCAL_FAN_OUT
        )) {
            fanOutService.fanOutToLocalSubscribers(event);
        }
        try (ChatRealtimeMeasurementRecorder.Timer ignored = measurementRecorder.start(
                event.message().clientMessageId(),
                ChatRealtimeMeasurementRecorder.REDIS_PUBLISH
        )) {
            redisPublisher.publish(event);
        }
    }
}
