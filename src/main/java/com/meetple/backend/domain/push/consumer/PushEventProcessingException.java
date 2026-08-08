package com.meetple.backend.domain.push.consumer;

public class PushEventProcessingException extends RuntimeException {

    public PushEventProcessingException(String message) {
        super(message);
    }

    public PushEventProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
