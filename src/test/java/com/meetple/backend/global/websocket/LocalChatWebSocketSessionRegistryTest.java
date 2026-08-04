package com.meetple.backend.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class LocalChatWebSocketSessionRegistryTest {

    private final LocalChatWebSocketSessionRegistry registry =
            new LocalChatWebSocketSessionRegistry();

    @Test
    void loginSessionInvalidationMatchesOnlySameLoginSession() {
        register("ws-1", 1L, "login-1", 10L);
        register("ws-2", 1L, "login-2", 10L);
        register("ws-3", 2L, "login-3", 10L);

        List<LocalChatWebSocketSessionRegistry.SessionSnapshot> targets =
                registry.findTargets(
                        ChatSessionInvalidationEvent.loginSession(1L, "login-1")
                );

        assertThat(targets)
                .extracting(LocalChatWebSocketSessionRegistry.SessionSnapshot::webSocketSessionId)
                .containsExactly("ws-1");
    }

    @Test
    void roomMemberAndRoomInvalidationUseCurrentSubscriptions() {
        register("ws-1", 1L, "login-1", 10L);
        register("ws-2", 2L, "login-2", 10L);
        register("ws-3", 1L, "login-1", 11L);

        assertThat(registry.findTargets(
                ChatSessionInvalidationEvent.participationCanceled(10L, 1L)
        ))
                .extracting(LocalChatWebSocketSessionRegistry.SessionSnapshot::webSocketSessionId)
                .containsExactly("ws-1");

        assertThat(registry.findTargets(
                ChatSessionInvalidationEvent.meetingCanceled(10L)
        ))
                .extracting(LocalChatWebSocketSessionRegistry.SessionSnapshot::webSocketSessionId)
                .containsExactlyInAnyOrder("ws-1", "ws-2");
    }

    @Test
    void unsubscribeRemovesRoomFromInvalidationTargets() {
        register("ws-1", 1L, "login-1", 10L);

        registry.unsubscribe("ws-1", "subscription-ws-1");

        assertThat(registry.findTargets(
                ChatSessionInvalidationEvent.meetingCanceled(10L)
        )).isEmpty();
    }

    private void register(
            String webSocketSessionId,
            Long memberId,
            String loginSessionId,
            Long roomId
    ) {
        WebSocketSession transportSession = org.mockito.Mockito.mock(WebSocketSession.class);
        given(transportSession.getId()).willReturn(webSocketSessionId);
        registry.registerTransport(transportSession);
        registry.authenticate(
                webSocketSessionId,
                memberId,
                loginSessionId,
                "access-token-" + webSocketSessionId,
                "principal-" + memberId
        );
        registry.subscribe(
                webSocketSessionId,
                "subscription-" + webSocketSessionId,
                roomId
        );
    }
}
