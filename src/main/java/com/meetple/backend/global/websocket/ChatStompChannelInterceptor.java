package com.meetple.backend.global.websocket;

import com.meetple.backend.domain.auth.repository.AccessTokenValidationRepository;
import com.meetple.backend.domain.chat.service.ChatAccessPolicy;
import com.meetple.backend.domain.chat.realtime.ChatMessageFanOutService;
import com.meetple.backend.domain.moderation.repository.MemberBlockRepository;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.AuthenticatedAccessToken;
import com.meetple.backend.global.security.AuthenticatedMember;
import com.meetple.backend.global.security.JwtTokenProvider;
import com.meetple.backend.global.security.JwtTokenSession;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
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
    private static final String USER_CONTROL_DESTINATION = "/user/queue/chat/control";
    private static final Duration OUTBOUND_AUTHORIZATION_REVALIDATION_TTL =
            Duration.ofSeconds(30);
    private static final Pattern ROOM_SUBSCRIPTION_PATTERN =
            Pattern.compile("^/topic/chat/rooms/(\\d+)$");
    private static final Pattern MESSAGE_SEND_PATTERN =
            Pattern.compile("^/app/chat/rooms/(\\d+)/messages$");

    private final JwtTokenProvider jwtTokenProvider;
    private final AccessTokenValidationRepository accessTokenValidationRepository;
    private final ChatAccessPolicy chatAccessPolicy;
    private final LocalChatWebSocketSessionRegistry sessionRegistry;
    private final MemberBlockRepository memberBlockRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null) {
            return authorizeOutboundMessageIfNecessary(message);
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return authorizeOutboundMessageIfNecessary(message);
        }

        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            StompHeaderAccessor mutableAccessor = mutableAccessor(message, accessor);
            mutableAccessor.setLeaveMutable(true);
            authenticate(mutableAccessor);
            return MessageBuilder.createMessage(
                    message.getPayload(),
                    mutableAccessor.getMessageHeaders()
            );
        }

        if (command == StompCommand.DISCONNECT) {
            sessionRegistry.remove(accessor.getSessionId());
            return message;
        }

        if (command == StompCommand.UNSUBSCRIBE) {
            sessionRegistry.unsubscribe(
                    accessor.getSessionId(),
                    accessor.getSubscriptionId()
            );
            return message;
        }

        if (command == StompCommand.MESSAGE) {
            return authorizeOutboundMessage(message, accessor);
        }

        if (command == StompCommand.SEND) {
            validateAuthenticatedSession(accessor);
            authorizeSendDestination(accessor.getDestination());
        } else if (command == StompCommand.SUBSCRIBE) {
            AuthenticatedMember member = validateAuthenticatedSession(accessor);
            Long roomId = subscriptionRoomId(accessor.getDestination());
            if (roomId != null) {
                beginSubscription(accessor, member.id(), roomId);
            }
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String accessToken = resolveAccessToken(accessor);
        Instant authenticationStartedAt = Instant.now();
        try {
            AuthenticatedAccessToken authenticatedToken = jwtTokenProvider.authenticateAccessToken(accessToken);
            Authentication authentication = authenticatedToken.authentication();
            AuthenticatedMember member = authenticatedMember(authentication);
            JwtTokenSession tokenSession = authenticatedToken.session();
            sessionRegistry.authenticate(
                    accessor.getSessionId(),
                    member.id(),
                    tokenSession.sessionId(),
                    accessToken,
                    authentication.getName(),
                    authenticationStartedAt,
                    authenticatedToken.expiresAt()
            );
            validateTokenSession(accessToken, member.id(), tokenSession);
            accessor.setUser(authentication);
            sessionAttributes(accessor).put(ACCESS_TOKEN_SESSION_ATTRIBUTE, accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            sessionRegistry.remove(accessor.getSessionId());
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

    private JwtTokenSession validateTokenSession(
            String accessToken,
            Long authenticatedMemberId
    ) {
        return validateTokenSession(
                accessToken,
                authenticatedMemberId,
                jwtTokenProvider.getAccessTokenSession(accessToken)
        );
    }

    private JwtTokenSession validateTokenSession(
            String accessToken,
            Long authenticatedMemberId,
            JwtTokenSession tokenSession
    ) {
        AccessTokenValidationRepository.Status status = accessTokenValidationRepository.getStatus(
                accessToken,
                tokenSession.memberId(),
                tokenSession.sessionId()
        );
        if (status == AccessTokenValidationRepository.Status.BLACKLISTED) {
            throw new IllegalArgumentException("로그아웃된 액세스 토큰입니다.");
        }
        if (!tokenSession.memberId().equals(authenticatedMemberId)
                || status == AccessTokenValidationRepository.Status.INACTIVE_SESSION) {
            throw new IllegalArgumentException("유효하지 않은 액세스 토큰 세션입니다.");
        }
        return tokenSession;
    }

    private Long subscriptionRoomId(String destination) {
        if (USER_ERROR_DESTINATION.equals(destination)
                || USER_CONTROL_DESTINATION.equals(destination)) {
            return null;
        }
        return extractRoomId(destination, ROOM_SUBSCRIPTION_PATTERN);
    }

    private void beginSubscription(
            StompHeaderAccessor accessor,
            Long memberId,
            Long roomId
    ) {
        String webSocketSessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        sessionRegistry.subscribe(
                webSocketSessionId,
                subscriptionId,
                roomId,
                Instant.now()
        );
        try {
            chatAccessPolicy.getRealtimeAccessibleMeeting(memberId, roomId);
        } catch (RuntimeException exception) {
            sessionRegistry.unsubscribe(webSocketSessionId, subscriptionId);
            throw exception;
        }
    }

    private void authorizeSendDestination(String destination) {
        extractRoomId(destination, MESSAGE_SEND_PATTERN);
    }

    private Message<?> authorizeOutboundMessage(
            Message<?> message,
            SimpMessageHeaderAccessor accessor
    ) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            return message;
        }
        Matcher matcher = ROOM_SUBSCRIPTION_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        Long roomId = Long.valueOf(matcher.group(1));
        Instant now = Instant.now();

        String subscriptionId = accessor.getSubscriptionId();
        if (!StringUtils.hasText(subscriptionId)) {
            return null;
        }

        LocalChatWebSocketSessionRegistry.OutboundAuthorization authorization = sessionRegistry
                .getOutboundAuthorization(
                        sessionId,
                        subscriptionId,
                        roomId,
                        now,
                        OUTBOUND_AUTHORIZATION_REVALIDATION_TTL
                )
                .orElse(null);
        if (authorization == null) {
            return null;
        }

        if (!authorization.accessTokenExpiresAt().isAfter(now)) {
            sessionRegistry.remove(sessionId);
            return null;
        }

        if (isBlockedSenderMessage(message, authorization.memberId())) {
            return null;
        }

        if (!authorization.requiresRevalidation()) {
            return message;
        }

        try {
            validateTokenSession(
                    authorization.accessToken(),
                    authorization.memberId()
            );
            chatAccessPolicy.getAccessibleMeeting(
                    authorization.memberId(),
                    roomId
            );
        } catch (JwtException | IllegalArgumentException exception) {
            sessionRegistry.remove(sessionId);
            return null;
        } catch (BaseException exception) {
            sessionRegistry.removeRoomSubscriptions(sessionId, roomId);
            return null;
        }
        sessionRegistry.markRoomAuthorizationValidated(sessionId, roomId, now);
        return message;
    }

    private boolean isBlockedSenderMessage(Message<?> message, Long recipientMemberId) {
        Object senderMemberId = message.getHeaders()
                .get(ChatMessageFanOutService.SENDER_MEMBER_ID_HEADER);
        return senderMemberId instanceof Number senderId
                && memberBlockRepository.existsByBlockerIdAndBlockedId(
                        recipientMemberId,
                        senderId.longValue()
                );
    }

    private Message<?> authorizeOutboundMessageIfNecessary(Message<?> message) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                SimpMessageHeaderAccessor.class
        );
        if (accessor == null || accessor.getMessageType() != SimpMessageType.MESSAGE) {
            return message;
        }
        return authorizeOutboundMessage(message, accessor);
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

    private StompHeaderAccessor mutableAccessor(
            Message<?> message,
            StompHeaderAccessor accessor
    ) {
        if (accessor.isMutable()) {
            return accessor;
        }
        return StompHeaderAccessor.wrap(message);
    }

    private BadCredentialsException invalidToken(Exception cause) {
        if (cause == null) {
            return new BadCredentialsException(ErrorStatus.INVALID_TOKEN.getMessage());
        }
        return new BadCredentialsException(ErrorStatus.INVALID_TOKEN.getMessage(), cause);
    }
}
