package com.meetple.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

    private final PasswordEncoderConfig passwordEncoderConfig = new PasswordEncoderConfig();

    @Test
    void passwordEncoderUsesOneWayHash() {
        PasswordEncoder passwordEncoder = passwordEncoderConfig.passwordEncoder();

        String encodedPassword = passwordEncoder.encode("password123");

        assertThat(encodedPassword).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", encodedPassword)).isTrue();
    }
}
