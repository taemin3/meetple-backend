package com.meetple.backend.global.websocket;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
                        List.of(10L)
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
}
