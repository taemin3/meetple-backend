package com.meetple.backend.domain.chat.realtime;

import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.SuccessStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageFanOutService {

    public static final String ROOM_TOPIC_PREFIX = "/topic/chat/rooms/";
    private static final Duration EVENT_DEDUPLICATION_TTL = Duration.ofMinutes(10);
    private static final int EVENT_DEDUPLICATION_CLEANUP_THRESHOLD = 10_000;
    private static final Duration EVENT_DEDUPLICATION_CLEANUP_INTERVAL =
            Duration.ofMinutes(1);

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<UUID, Instant> handledEventIds = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> nextEventCleanupAt =
            new AtomicReference<>(Instant.EPOCH);

    public void fanOutToLocalSubscribers(ChatMessageFanOutEvent event) {
        Instant handledAt = Instant.now();
        if (handledEventIds.putIfAbsent(event.eventId(), handledAt) != null) {
            return;
        }
        cleanupHandledEventsIfNecessary(handledAt);

        try {
            messagingTemplate.convertAndSend(
                    ROOM_TOPIC_PREFIX + event.message().roomId(),
                    ApiResponse.successBody(SuccessStatus.OK, event.message())
            );
        } catch (RuntimeException exception) {
            handledEventIds.remove(event.eventId());
            log.error(
                    "채팅 메시지를 로컬 WebSocket 구독자에게 전송하지 못했습니다. eventId={}, roomId={}",
                    event.eventId(),
                    event.message().roomId(),
                    exception
            );
        }
    }

    private void cleanupHandledEventsIfNecessary(Instant now) {
        if (handledEventIds.size() < EVENT_DEDUPLICATION_CLEANUP_THRESHOLD) {
            return;
        }
        Instant scheduledCleanupAt = nextEventCleanupAt.get();
        if (now.isBefore(scheduledCleanupAt)
                || !nextEventCleanupAt.compareAndSet(
                        scheduledCleanupAt,
                        now.plus(EVENT_DEDUPLICATION_CLEANUP_INTERVAL)
                )) {
            return;
        }
        Instant expirationBoundary = now.minus(EVENT_DEDUPLICATION_TTL);
        handledEventIds.entrySet().removeIf(
                entry -> entry.getValue().isBefore(expirationBoundary)
        );
    }
}
