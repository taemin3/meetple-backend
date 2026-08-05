package com.meetple.backend.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.chat.service.ChatAccessPolicy;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.security.AuthenticatedMember;
import com.meetple.backend.global.security.JwtTokenProvider;
import com.meetple.backend.global.security.JwtTokenSession;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ChatStompChannelInterceptorTest {

    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ChatAccessPolicy chatAccessPolicy;

    @Mock
    private LocalChatWebSocketSessionRegistry sessionRegistry;

    @InjectMocks
    private ChatStompChannelInterceptor interceptor;

    @Test
    void connectAuthenticatesAccessTokenAndStoresSessionToken() {
        Authentication authentication = authentication(1L);
        given(jwtTokenProvider.getAuthentication(ACCESS_TOKEN)).willReturn(authentication);
        given(jwtTokenProvider.getAccessTokenSession(ACCESS_TOKEN))
                .willReturn(new JwtTokenSession(1L, "session-1"));
        given(refreshTokenRepository.existsByMemberIdAndSessionId(1L, "session-1"))
                .willReturn(true);
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT, null, null);
        accessor.setNativeHeader("Authorization", "Bearer " + ACCESS_TOKEN);

        Message<?> intercepted = interceptor.preSend(message(accessor), null);
        StompHeaderAccessor interceptedAccessor = StompHeaderAccessor.wrap(intercepted);

        assertThat(interceptedAccessor.getUser()).isEqualTo(authentication);
        assertThat(interceptedAccessor.getSessionAttributes())
                .containsEntry("chatAccessToken", ACCESS_TOKEN);
        InOrder inOrder = inOrder(sessionRegistry, refreshTokenRepository);
        inOrder.verify(sessionRegistry).authenticate(
                eq("session-1"),
                eq(1L),
                eq("session-1"),
                eq(ACCESS_TOKEN),
                eq(authentication.getName()),
                any(Instant.class)
        );
        inOrder.verify(refreshTokenRepository)
                .existsByMemberIdAndSessionId(1L, "session-1");
    }

    @Test
    void connectRejectsMissingAuthorizationHeader() {
        Message<byte[]> message = message(accessor(StompCommand.CONNECT, null, null));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("유효하지 않은 토큰입니다.");
    }

    @Test
    void stompCommandAuthenticatesAccessTokenAndStoresSessionToken() {
        Authentication authentication = authentication(1L);
        given(jwtTokenProvider.getAuthentication(ACCESS_TOKEN)).willReturn(authentication);
        given(jwtTokenProvider.getAccessTokenSession(ACCESS_TOKEN))
                .willReturn(new JwtTokenSession(1L, "session-1"));
        given(refreshTokenRepository.existsByMemberIdAndSessionId(1L, "session-1"))
                .willReturn(true);
        StompHeaderAccessor accessor = accessor(StompCommand.STOMP, null, null);
        accessor.setNativeHeader("Authorization", "Bearer " + ACCESS_TOKEN);

        Message<?> intercepted = interceptor.preSend(message(accessor), null);
        StompHeaderAccessor interceptedAccessor = StompHeaderAccessor.wrap(intercepted);

        assertThat(interceptedAccessor.getUser()).isEqualTo(authentication);
        assertThat(interceptedAccessor.getSessionAttributes())
                .containsEntry("chatAccessToken", ACCESS_TOKEN);
    }

    @Test
    void connectRemovesPendingAuthenticationWhenTokenSessionIsInvalid() {
        Authentication authentication = authentication(1L);
        given(jwtTokenProvider.getAuthentication(ACCESS_TOKEN)).willReturn(authentication);
        given(jwtTokenProvider.getAccessTokenSession(ACCESS_TOKEN))
                .willReturn(new JwtTokenSession(1L, "session-1"));
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT, null, null);
        accessor.setNativeHeader("Authorization", "Bearer " + ACCESS_TOKEN);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("유효하지 않은 토큰입니다.");

        verify(sessionRegistry).authenticate(
                eq("session-1"),
                eq(1L),
                eq("session-1"),
                eq(ACCESS_TOKEN),
                eq(authentication.getName()),
                any(Instant.class)
        );
        verify(sessionRegistry).remove("session-1");
    }

    @Test
    void subscribeChecksTokenSessionAndRoomAccess() {
        stubActiveSession();
        StompHeaderAccessor accessor = accessor(
                StompCommand.SUBSCRIBE,
                "/topic/chat/rooms/10",
                authentication(1L)
        );

        interceptor.preSend(message(accessor), null);

        InOrder inOrder = inOrder(sessionRegistry, chatAccessPolicy);
        inOrder.verify(sessionRegistry).subscribe(
                eq("session-1"),
                eq("subscription-1"),
                eq(10L),
                any(Instant.class)
        );
        inOrder.verify(chatAccessPolicy).getRealtimeAccessibleMeeting(1L, 10L);
    }

    @Test
    void subscribeRollsBackPendingRegistrationWhenRoomAccessIsDenied() {
        stubActiveSession();
        given(chatAccessPolicy.getRealtimeAccessibleMeeting(1L, 10L))
                .willThrow(new ForbiddenException("채팅방 입장 권한이 없습니다."));
        StompHeaderAccessor accessor = accessor(
                StompCommand.SUBSCRIBE,
                "/topic/chat/rooms/10",
                authentication(1L)
        );

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("채팅방 입장 권한이 없습니다.");

        verify(sessionRegistry).unsubscribe("session-1", "subscription-1");
    }

    @Test
    void subscribeRejectsUnsupportedDestination() {
        stubActiveSession();
        StompHeaderAccessor accessor = accessor(
                StompCommand.SUBSCRIBE,
                "/topic/admin",
                authentication(1L)
        );

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 STOMP 목적지입니다.");

        verify(chatAccessPolicy, never()).getRealtimeAccessibleMeeting(1L, 10L);
    }

    @Test
    void sendRejectsDirectBrokerDestination() {
        stubActiveSession();
        StompHeaderAccessor accessor = accessor(
                StompCommand.SEND,
                "/topic/chat/rooms/10",
                authentication(1L)
        );

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 STOMP 목적지입니다.");
    }

    @Test
    void sendRejectsBlacklistedConnectedSession() {
        given(accessTokenBlacklistRepository.exists(ACCESS_TOKEN)).willReturn(true);
        StompHeaderAccessor accessor = accessor(
                StompCommand.SEND,
                "/app/chat/rooms/10/messages",
                authentication(1L)
        );

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("유효하지 않은 토큰입니다.");
    }

    @Test
    void outboundRoomMessageRevalidatesTokenAndRoomAccess() {
        connectSession();
        Message<?> result = interceptor.preSend(outboundMessage(10L), null);

        assertThat(result).isNotNull();
        verify(chatAccessPolicy).getAccessibleMeeting(1L, 10L);
    }

    @Test
    void outboundRoomMessageIsDroppedAfterParticipationAccessIsRevoked() {
        connectSession();
        given(chatAccessPolicy.getAccessibleMeeting(1L, 10L))
                .willThrow(new ForbiddenException("채팅방 입장 권한이 없습니다."));
        Message<?> revokedRoomResult = interceptor.preSend(outboundMessage(10L), null);
        Message<?> otherRoomResult = interceptor.preSend(outboundMessage(11L), null);

        assertThat(revokedRoomResult).isNull();
        assertThat(otherRoomResult).isNotNull();
        verify(chatAccessPolicy).getAccessibleMeeting(1L, 11L);
    }

    @Test
    void outboundRoomMessageIsDroppedAfterLogout() {
        connectSession();
        given(accessTokenBlacklistRepository.exists(ACCESS_TOKEN)).willReturn(true);
        Message<?> result = interceptor.preSend(outboundMessage(10L), null);

        assertThat(result).isNull();
        verify(chatAccessPolicy, never()).getAccessibleMeeting(1L, 10L);
    }

    private void stubActiveSession() {
        given(jwtTokenProvider.getAccessTokenSession(ACCESS_TOKEN))
                .willReturn(new JwtTokenSession(1L, "session-1"));
        given(refreshTokenRepository.existsByMemberIdAndSessionId(1L, "session-1"))
                .willReturn(true);
    }

    private void connectSession() {
        Authentication authentication = authentication(1L);
        given(jwtTokenProvider.getAuthentication(ACCESS_TOKEN)).willReturn(authentication);
        stubActiveSession();
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT, null, null);
        accessor.setNativeHeader("Authorization", "Bearer " + ACCESS_TOKEN);
        interceptor.preSend(message(accessor), null);
        given(sessionRegistry.getAuthenticatedSession("session-1"))
                .willReturn(Optional.of(
                        new LocalChatWebSocketSessionRegistry.AuthenticatedSession(
                                1L,
                                "session-1",
                                ACCESS_TOKEN
                        )
                ));
    }

    private Authentication authentication(Long memberId) {
        AuthenticatedMember member = new AuthenticatedMember(
                memberId,
                "member@meetple.com",
                MemberRole.USER
        );
        return new UsernamePasswordAuthenticationToken(member, null, List.of());
    }

    private StompHeaderAccessor accessor(
            StompCommand command,
            String destination,
        Authentication authentication
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (command != StompCommand.CONNECT && command != StompCommand.STOMP) {
            sessionAttributes.put("chatAccessToken", ACCESS_TOKEN);
        }
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setSessionId("session-1");
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (command == StompCommand.SUBSCRIBE) {
            accessor.setSubscriptionId("subscription-1");
        }
        if (authentication != null) {
            accessor.setUser(authentication);
        }
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> outboundMessage(Long roomId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(
                SimpMessageType.MESSAGE
        );
        accessor.setDestination("/topic/chat/rooms/" + roomId);
        accessor.setSessionId("session-1");
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
