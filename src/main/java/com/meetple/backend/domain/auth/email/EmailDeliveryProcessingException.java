package com.meetple.backend.domain.auth.email;

public class EmailDeliveryProcessingException extends RuntimeException {

    public EmailDeliveryProcessingException(String message) {
        super(message);
    }

    public EmailDeliveryProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
