package com.meetple.backend.domain.ai;

public class NonRetryableMeetingEmbeddingException extends RuntimeException {
    public NonRetryableMeetingEmbeddingException(String message) {
        super(message);
    }

    public NonRetryableMeetingEmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
