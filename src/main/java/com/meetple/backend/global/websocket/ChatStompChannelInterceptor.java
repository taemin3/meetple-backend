package com.meetple.backend.global.websocket;

import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.chat.service.ChatAccessPolicy;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import com.meetple.backend.global.security.JwtTokenProvider;
import com.meetple.backend.global.security.JwtTokenSession;
import io.jsonwebtoken.JwtException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ChatStompChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCESS_TOKEN_SESSION_ATTRIBUTE = "chatAccessToken";
    private static final String USER_ERROR_DESTINATION = "/user/queue/chat/errors";
    private static final Pattern ROOM_SUBSCRIPTION_PATTERN =
            Pattern.compile("^/topic/chat/rooms/(\\d+)$");
    private static final Pattern MESSAGE_SEND_PATTERN =
            Pattern.compile("^/app/chat/rooms/(\\d+)/messages$");

    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ChatAccessPolicy chatAccessPolicy;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
            return message;
        }

        if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            AuthenticatedMember member = validateAuthenticatedSession(accessor);
            if (command == StompCommand.SUBSCRIBE) {
                authorizeSubscription(member.id(), accessor.getDestination());
            } else {
                authorizeSendDestination(accessor.getDestination());
            }
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String accessToken = resolveAccessToken(accessor);
        try {
            Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);
            validateTokenSession(accessToken, authenticatedMember(authentication).id());
            accessor.setUser(authentication);
            sessionAttributes(accessor).put(ACCESS_TOKEN_SESSION_ATTRIBUTE, accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidToken(e);
        }
    }

    private AuthenticatedMember validateAuthenticatedSession(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication)) {
            throw invalidToken(null);
        }
        AuthenticatedMember member = authenticatedMember(authentication);
        Object storedToken = sessionAttributes(accessor).get(ACCESS_TOKEN_SESSION_ATTRIBUTE);
        if (!(storedToken instanceof String accessToken) || !StringUtils.hasText(accessToken)) {
            throw invalidToken(null);
        }

        try {
            validateTokenSession(accessToken, member.id());
        } catch (JwtException | IllegalArgumentException e) {
            throw invalidToken(e);
        }
        return member;
    }

    private void validateTokenSession(String accessToken, Long authenticatedMemberId) {
        if (accessTokenBlacklistRepository.exists(accessToken)) {
            throw new IllegalArgumentException("로그아웃된 액세스 토큰입니다.");
        }
        JwtTokenSession tokenSession = jwtTokenProvider.getAccessTokenSession(accessToken);
        if (!tokenSession.memberId().equals(authenticatedMemberId)
                || !refreshTokenRepository.existsByMemberIdAndSessionId(
                        tokenSession.memberId(),
                        tokenSession.sessionId()
                )) {
            throw new IllegalArgumentException("유효하지 않은 액세스 토큰 세션입니다.");
        }
    }

    private void authorizeSubscription(Long memberId, String destination) {
        if (USER_ERROR_DESTINATION.equals(destination)) {
            return;
        }
        Long roomId = extractRoomId(destination, ROOM_SUBSCRIPTION_PATTERN);
        chatAccessPolicy.getAccessibleMeeting(memberId, roomId);
    }

    private void authorizeSendDestination(String destination) {
        extractRoomId(destination, MESSAGE_SEND_PATTERN);
    }

    private Long extractRoomId(String destination, Pattern pattern) {
        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("허용되지 않은 STOMP 목적지입니다.");
        }
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            throw new AccessDeniedException("허용되지 않은 STOMP 목적지입니다.");
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("올바르지 않은 채팅방 번호입니다.", e);
        }
    }

    private String resolveAccessToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)) {
            authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION.toLowerCase());
        }
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw invalidToken(null);
        }
        String accessToken = authorization.substring(BEARER_PREFIX.length());
        if (!StringUtils.hasText(accessToken)) {
            throw invalidToken(null);
        }
        return accessToken;
    }

    private AuthenticatedMember authenticatedMember(Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthenticatedMember member) {
            return member;
        }
        throw invalidToken(null);
    }

    private Map<String, Object> sessionAttributes(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            throw invalidToken(null);
        }
        return attributes;
    }

    private BadCredentialsException invalidToken(Exception cause) {
        if (cause == null) {
            return new BadCredentialsException(ErrorStatus.INVALID_TOKEN.getMessage());
        }
        return new BadCredentialsException(ErrorStatus.INVALID_TOKEN.getMessage(), cause);
    }
}
