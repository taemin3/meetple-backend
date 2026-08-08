package com.meetple.backend.domain.push.fcm;

public class PushSendException extends RuntimeException {

    private final String errorCode;
    private final PushSendResult partialResult;

    public PushSendException(String errorCode, Throwable cause) {
        this(errorCode, new PushSendResult(
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        ), cause);
    }

    public PushSendException(
            String errorCode,
            PushSendResult partialResult,
            Throwable cause
    ) {
        super("FCM push delivery failed: " + errorCode, cause);
        this.errorCode = errorCode;
        this.partialResult = partialResult;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public PushSendResult getPartialResult() {
        return partialResult;
    }
}
