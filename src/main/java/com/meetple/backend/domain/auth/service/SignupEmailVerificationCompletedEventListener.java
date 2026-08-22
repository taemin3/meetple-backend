package com.meetple.backend.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class SignupEmailVerificationCompletedEventListener {

    private final EmailVerificationService emailVerificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SignupEmailVerificationCompletedEvent event) {
        try {
            emailVerificationService.consumeSignupToken(
                    event.email(),
                    event.signupVerificationToken()
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to consume signup email verification token after commit", exception);
        }
    }
}
