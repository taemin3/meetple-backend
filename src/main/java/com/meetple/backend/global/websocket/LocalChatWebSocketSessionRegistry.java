package com.meetple.backend.global.websocket;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

@Component
public class LocalChatWebSocketSessionRegistry {

    private final Map<String, SessionRegistration> sessions = new ConcurrentHashMap<>();

    public void registerTransport(WebSocketSession transportSession) {
        sessions.put(
                transportSession.getId(),
                new SessionRegistration(transportSession)
        );
    }

    public void authenticate(
            String webSocketSessionId,
            Long memberId,
            String loginSessionId,
            String accessToken,
            String principalName,
            Instant authenticatedAt
    ) {
        SessionRegistration registration = sessions.get(webSocketSessionId);
        if (registration != null) {
            registration.authenticate(
                    memberId,
                    loginSessionId,
                    accessToken,
                    principalName,
                    authenticatedAt
            );
        }
    }

    public void subscribe(
            String webSocketSessionId,
            String subscriptionId,
            Long roomId,
            Instant subscribedAt
    ) {
        SessionRegistration registration = sessions.get(webSocketSessionId);
        if (registration != null && StringUtils.hasText(subscriptionId)) {
            registration.subscribe(subscriptionId, roomId, subscribedAt);
        }
    }

    public void unsubscribe(String webSocketSessionId, String subscriptionId) {
        SessionRegistration registration = sessions.get(webSocketSessionId);
        if (registration != null && StringUtils.hasText(subscriptionId)) {
            registration.unsubscribe(subscriptionId);
        }
    }

    public void remove(String webSocketSessionId) {
        if (StringUtils.hasText(webSocketSessionId)) {
            sessions.remove(webSocketSessionId);
        }
    }

    public Optional<AuthenticatedSession> getAuthenticatedSession(String webSocketSessionId) {
        SessionRegistration registration = sessions.get(webSocketSessionId);
        return registration == null
                ? Optional.empty()
                : registration.authenticatedSession();
    }

    public List<SessionSnapshot> findTargets(ChatSessionInvalidationEvent event) {
        return sessions.values().stream()
                .map(SessionRegistration::snapshot)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(snapshot -> snapshot.matches(event))
                .toList();
    }

    public record AuthenticatedSession(
            Long memberId,
            String loginSessionId,
            String accessToken
    ) {
    }

    public record SessionSnapshot(
            String webSocketSessionId,
            WebSocketSession transportSession,
            Long memberId,
            String loginSessionId,
            String principalName,
            Instant authenticatedAt,
            List<RoomSubscription> roomSubscriptions
    ) {

        private boolean matches(ChatSessionInvalidationEvent event) {
            return switch (event.target()) {
                case LOGIN_SESSION -> memberId.equals(event.memberId())
                        && loginSessionId.equals(event.loginSessionId())
                        && existedAt(event.occurredAt());
                case MEMBER -> memberId.equals(event.memberId())
                        && existedAt(event.occurredAt());
                case ROOM_MEMBER -> memberId.equals(event.memberId())
                        && subscribedAt(event.roomId(), event.occurredAt());
                case ROOM -> subscribedAt(event.roomId(), event.occurredAt());
            };
        }

        private boolean existedAt(Instant occurredAt) {
            return !authenticatedAt.isAfter(occurredAt);
        }

        private boolean subscribedAt(Long roomId, Instant occurredAt) {
            return roomSubscriptions.stream()
                    .anyMatch(subscription -> subscription.roomId().equals(roomId)
                            && !subscription.subscribedAt().isAfter(occurredAt));
        }
    }

    public record RoomSubscription(
            Long roomId,
            Instant subscribedAt
    ) {
    }

    private static final class SessionRegistration {

        private final WebSocketSession transportSession;
        private final Map<String, RoomSubscription> roomsBySubscription =
                new ConcurrentHashMap<>();
        private volatile Long memberId;
        private volatile String loginSessionId;
        private volatile String accessToken;
        private volatile String principalName;
        private volatile Instant authenticatedAt;

        private SessionRegistration(WebSocketSession transportSession) {
            this.transportSession = transportSession;
        }

        private void authenticate(
                Long memberId,
                String loginSessionId,
                String accessToken,
                String principalName,
                Instant authenticatedAt
        ) {
            this.memberId = memberId;
            this.loginSessionId = loginSessionId;
            this.accessToken = accessToken;
            this.principalName = principalName;
            this.authenticatedAt = authenticatedAt;
        }

        private void subscribe(
                String subscriptionId,
                Long roomId,
                Instant subscribedAt
        ) {
            roomsBySubscription.put(
                    subscriptionId,
                    new RoomSubscription(roomId, subscribedAt)
            );
        }

        private void unsubscribe(String subscriptionId) {
            roomsBySubscription.remove(subscriptionId);
        }

        private Optional<AuthenticatedSession> authenticatedSession() {
            if (memberId == null
                    || !StringUtils.hasText(loginSessionId)
                    || !StringUtils.hasText(accessToken)) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedSession(
                    memberId,
                    loginSessionId,
                    accessToken
            ));
        }

        private Optional<SessionSnapshot> snapshot() {
            if (memberId == null
                    || !StringUtils.hasText(loginSessionId)
                    || !StringUtils.hasText(principalName)
                    || authenticatedAt == null) {
                return Optional.empty();
            }
            return Optional.of(new SessionSnapshot(
                    transportSession.getId(),
                    transportSession,
                    memberId,
                    loginSessionId,
                    principalName,
                    authenticatedAt,
                    List.copyOf(roomsBySubscription.values())
            ));
        }
    }
}
