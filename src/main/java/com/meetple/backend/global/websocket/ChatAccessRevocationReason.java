package com.meetple.backend.global.websocket;

public enum ChatAccessRevocationReason {
    LOGIN_SESSION_LOGOUT,
    MEMBER_LOGOUT_ALL,
    MEMBER_SUSPENDED,
    PARTICIPATION_CANCELED,
    PARTICIPATION_APPROVAL_REVOKED,
    MEETING_CANCELED
}
