package com.meetple.backend.domain.auth.email;

public class NonRetryableEmailDeliveryException extends RuntimeException {

    public NonRetryableEmailDeliveryException(String message) {
        super(message);
    }

    public NonRetryableEmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
