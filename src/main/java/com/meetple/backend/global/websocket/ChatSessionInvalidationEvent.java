package com.meetple.backend.global.websocket;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionInvalidationEvent(
        int schemaVersion,
        UUID eventId,
        ChatSessionInvalidationTarget target,
        Long memberId,
        String loginSessionId,
        Long roomId,
        ChatAccessRevocationReason reason,
        Instant occurredAt
) {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    public ChatSessionInvalidationEvent withOccurredAt(Instant occurredAt) {
        return new ChatSessionInvalidationEvent(
                schemaVersion,
                eventId,
                target,
                memberId,
                loginSessionId,
                roomId,
                reason,
                occurredAt
        );
    }

    public static ChatSessionInvalidationEvent loginSession(
            Long memberId,
            String loginSessionId
    ) {
        return create(
                ChatSessionInvalidationTarget.LOGIN_SESSION,
                memberId,
                loginSessionId,
                null,
                ChatAccessRevocationReason.LOGIN_SESSION_LOGOUT
        );
    }

    public static ChatSessionInvalidationEvent member(Long memberId) {
        return create(
                ChatSessionInvalidationTarget.MEMBER,
                memberId,
                null,
                null,
                ChatAccessRevocationReason.MEMBER_LOGOUT_ALL
        );
    }

    public static ChatSessionInvalidationEvent participationCanceled(
            Long roomId,
            Long memberId
    ) {
        return roomMember(
                roomId,
                memberId,
                ChatAccessRevocationReason.PARTICIPATION_CANCELED
        );
    }

    public static ChatSessionInvalidationEvent participationApprovalRevoked(
            Long roomId,
            Long memberId
    ) {
        return roomMember(
                roomId,
                memberId,
                ChatAccessRevocationReason.PARTICIPATION_APPROVAL_REVOKED
        );
    }

    public static ChatSessionInvalidationEvent meetingCanceled(Long roomId) {
        return create(
                ChatSessionInvalidationTarget.ROOM,
                null,
                null,
                roomId,
                ChatAccessRevocationReason.MEETING_CANCELED
        );
    }

    private static ChatSessionInvalidationEvent roomMember(
            Long roomId,
            Long memberId,
            ChatAccessRevocationReason reason
    ) {
        return create(
                ChatSessionInvalidationTarget.ROOM_MEMBER,
                memberId,
                null,
                roomId,
                reason
        );
    }

    private static ChatSessionInvalidationEvent create(
            ChatSessionInvalidationTarget target,
            Long memberId,
            String loginSessionId,
            Long roomId,
            ChatAccessRevocationReason reason
    ) {
        return new ChatSessionInvalidationEvent(
                CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                target,
                memberId,
                loginSessionId,
                roomId,
                reason,
                Instant.now()
        );
    }
}
