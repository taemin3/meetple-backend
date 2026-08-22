package com.meetple.backend.domain.auth.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationSecretGenerator {

    private static final int CODE_BOUND = 1_000_000;
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_BOUND));
    }

    public String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
