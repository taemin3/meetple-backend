package com.meetple.backend.domain.auth.service;

import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.mail.EmailVerificationMailSender;
import com.meetple.backend.domain.auth.repository.PasswordResetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetMailRequestedEventListener {

    private final EmailVerificationMailSender emailVerificationMailSender;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailVerificationProperties properties;

    @Async("passwordResetMailExecutor")
    @EventListener
    public void handle(PasswordResetMailRequestedEvent event) {
        if (!event.deliver()) {
            return;
        }

        try {
            emailVerificationMailSender.sendPasswordResetCode(
                    event.email(),
                    event.code(),
                    properties.codeTtl()
            );
        } catch (RuntimeException exception) {
            passwordResetRepository.deleteChallengeIfMatches(
                    event.email(),
                    event.codeHash()
            );
            log.warn("Failed to deliver password reset verification mail", exception);
        }
    }
}
