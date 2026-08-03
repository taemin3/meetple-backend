package com.meetple.backend.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.chat.service.ChatAccessPolicy;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.security.AuthenticatedMember;
import com.meetple.backend.global.security.JwtTokenProvider;
import com.meetple.backend.global.security.JwtTokenSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
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

        interceptor.preSend(message(accessor), null);

        assertThat(accessor.getUser()).isEqualTo(authentication);
        assertThat(accessor.getSessionAttributes())
                .containsEntry("chatAccessToken", ACCESS_TOKEN);
    }

    @Test
    void connectRejectsMissingAuthorizationHeader() {
        Message<byte[]> message = message(accessor(StompCommand.CONNECT, null, null));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("유효하지 않은 토큰입니다.");
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

        verify(chatAccessPolicy).getAccessibleMeeting(1L, 10L);
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

        verify(chatAccessPolicy, never()).getAccessibleMeeting(1L, 10L);
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

    private void stubActiveSession() {
        given(jwtTokenProvider.getAccessTokenSession(ACCESS_TOKEN))
                .willReturn(new JwtTokenSession(1L, "session-1"));
        given(refreshTokenRepository.existsByMemberIdAndSessionId(1L, "session-1"))
                .willReturn(true);
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
        accessor.setSessionAttributes(new HashMap<>(Map.of(
                "chatAccessToken",
                ACCESS_TOKEN
        )));
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authentication != null) {
            accessor.setUser(authentication);
        }
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
