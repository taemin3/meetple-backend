package com.meetple.backend.domain.image.consumer;

public class NonRetryableImageDeletionEventException extends RuntimeException {

    public NonRetryableImageDeletionEventException(String message) {
        super(message);
    }

    public NonRetryableImageDeletionEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
