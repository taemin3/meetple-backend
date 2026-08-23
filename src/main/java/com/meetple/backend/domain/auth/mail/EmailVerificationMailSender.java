package com.meetple.backend.domain.auth.mail;

public interface EmailVerificationMailSender {

    void sendVerificationCode(String recipient, String code);

    void sendPasswordResetCode(String recipient, String code);
}
