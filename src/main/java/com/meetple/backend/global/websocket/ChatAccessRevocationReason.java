package com.meetple.backend.global.websocket;

public enum ChatAccessRevocationReason {
    LOGIN_SESSION_LOGOUT,
    MEMBER_LOGOUT_ALL,
    PARTICIPATION_CANCELED,
    PARTICIPATION_APPROVAL_REVOKED,
    MEETING_CANCELED
}
