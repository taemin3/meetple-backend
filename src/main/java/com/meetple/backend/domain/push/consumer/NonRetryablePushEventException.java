package com.meetple.backend.domain.push.consumer;

public class NonRetryablePushEventException extends PushEventProcessingException {

    public NonRetryablePushEventException(String message) {
        super(message);
    }

    public NonRetryablePushEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
