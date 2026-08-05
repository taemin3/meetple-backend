package com.meetple.backend.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class ChatSessionInvalidationServiceTest {

    @Mock
    private LocalChatWebSocketSessionRegistry sessionRegistry;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private WebSocketSession transportSession;

    @InjectMocks
    private ChatSessionInvalidationService invalidationService;

    @Test
    void sendsControlMessageAndClosesTargetSessionOnlyOnce() throws Exception {
        ChatSessionInvalidationEvent event =
                ChatSessionInvalidationEvent.meetingCanceled(10L);
        LocalChatWebSocketSessionRegistry.SessionSnapshot session =
                new LocalChatWebSocketSessionRegistry.SessionSnapshot(
                        "ws-1",
                        transportSession,
                        1L,
                        "login-1",
                        "principal-1",
                        Instant.EPOCH,
                        List.of(new LocalChatWebSocketSessionRegistry.RoomSubscription(
                                10L,
                                Instant.EPOCH
                        ))
                );
        given(sessionRegistry.findTargets(event)).willReturn(List.of(session));
        given(transportSession.isOpen()).willReturn(true);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        invalidationService.invalidateLocalSessions(event);
        invalidationService.invalidateLocalSessions(event);

        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("principal-1"),
                eq(ChatSessionInvalidationService.CONTROL_DESTINATION),
                eq(ChatAccessRevokedMessage.from(event)),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any()
        );
        verify(transportSession).close(CloseStatus.POLICY_VIOLATION);
        verify(sessionRegistry).remove("ws-1");
    }

    @Test
    void throttlesDeduplicationCleanupAfterThreshold() throws Exception {
        ChatSessionInvalidationService service = new ChatSessionInvalidationService(
                new LocalChatWebSocketSessionRegistry(),
                messagingTemplate,
                taskScheduler
        );
        for (int index = 0; index < 10_000; index++) {
            service.invalidateLocalSessions(
                    ChatSessionInvalidationEvent.meetingCanceled(10L)
            );
        }
        AtomicReference<Instant> nextCleanupAt = nextCleanupAt(service);
        Instant scheduledCleanupAt = nextCleanupAt.get();

        service.invalidateLocalSessions(
                ChatSessionInvalidationEvent.meetingCanceled(10L)
        );

        assertThat(scheduledCleanupAt).isAfter(Instant.now());
        assertThat(nextCleanupAt.get()).isEqualTo(scheduledCleanupAt);
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<Instant> nextCleanupAt(
            ChatSessionInvalidationService service
    ) throws Exception {
        Field field = ChatSessionInvalidationService.class.getDeclaredField(
                "nextEventCleanupAt"
        );
        field.setAccessible(true);
        return (AtomicReference<Instant>) field.get(service);
    }
}
