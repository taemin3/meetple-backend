package com.meetple.backend.domain.auth.mail;

import java.time.Duration;

public interface EmailVerificationMailSender {

    void sendVerificationCode(String recipient, String code, Duration expiresIn);
}
