package com.meetple.backend.global.websocket;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;

@Slf4j
@Service
public class ChatSessionInvalidationService {

    public static final String CONTROL_DESTINATION = "/queue/chat/control";
    private static final Duration CLOSE_GRACE_PERIOD = Duration.ofMillis(250);
    private static final Duration EVENT_DEDUPLICATION_TTL = Duration.ofMinutes(10);
    private static final int EVENT_DEDUPLICATION_CLEANUP_THRESHOLD = 10_000;

    private final LocalChatWebSocketSessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler taskScheduler;
    private final Map<UUID, Instant> handledEventIds = new ConcurrentHashMap<>();

    public ChatSessionInvalidationService(
            LocalChatWebSocketSessionRegistry sessionRegistry,
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("chatMessageBrokerTaskScheduler") TaskScheduler taskScheduler
    ) {
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
        this.taskScheduler = taskScheduler;
    }

    public void invalidateLocalSessions(ChatSessionInvalidationEvent event) {
        if (handledEventIds.putIfAbsent(event.eventId(), Instant.now()) != null) {
            return;
        }
        cleanupHandledEventsIfNecessary();

        ChatAccessRevokedMessage message = ChatAccessRevokedMessage.from(event);
        sessionRegistry.findTargets(event).forEach(session -> {
            try {
                sendControlMessage(session, message);
            } catch (RuntimeException exception) {
                log.warn(
                        "채팅 접근 무효화 제어 메시지를 전송하지 못했습니다. sessionId={}",
                        session.webSocketSessionId(),
                        exception
                );
            }
            scheduleClose(session);
        });
    }

    private void sendControlMessage(
            LocalChatWebSocketSessionRegistry.SessionSnapshot session,
            ChatAccessRevokedMessage message
    ) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(
                SimpMessageType.MESSAGE
        );
        headers.setSessionId(session.webSocketSessionId());
        headers.setLeaveMutable(true);
        messagingTemplate.convertAndSendToUser(
                session.principalName(),
                CONTROL_DESTINATION,
                message,
                headers.getMessageHeaders()
        );
    }

    private void scheduleClose(LocalChatWebSocketSessionRegistry.SessionSnapshot session) {
        Runnable closeTask = () -> closeSession(session);
        try {
            taskScheduler.schedule(
                    closeTask,
                    Instant.now().plus(CLOSE_GRACE_PERIOD)
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "채팅 세션 종료 작업을 예약하지 못해 즉시 종료합니다. sessionId={}",
                    session.webSocketSessionId(),
                    exception
            );
            closeTask.run();
        }
    }

    private void closeSession(LocalChatWebSocketSessionRegistry.SessionSnapshot session) {
        try {
            if (session.transportSession().isOpen()) {
                session.transportSession().close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (IOException exception) {
            log.warn(
                    "무효화된 채팅 WebSocket 세션을 종료하지 못했습니다. sessionId={}",
                    session.webSocketSessionId(),
                    exception
            );
        } finally {
            sessionRegistry.remove(session.webSocketSessionId());
        }
    }

    private void cleanupHandledEventsIfNecessary() {
        if (handledEventIds.size() < EVENT_DEDUPLICATION_CLEANUP_THRESHOLD) {
            return;
        }
        Instant expirationBoundary = Instant.now().minus(EVENT_DEDUPLICATION_TTL);
        handledEventIds.entrySet().removeIf(
                entry -> entry.getValue().isBefore(expirationBoundary)
        );
    }
}
