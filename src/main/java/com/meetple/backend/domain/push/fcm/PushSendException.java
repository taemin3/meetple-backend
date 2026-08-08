package com.meetple.backend.domain.push.fcm;

public class PushSendException extends RuntimeException {

    private final String errorCode;

    public PushSendException(String errorCode, Throwable cause) {
        super("FCM push delivery failed: " + errorCode, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
