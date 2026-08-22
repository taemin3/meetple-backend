package com.meetple.backend.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SignupEmailVerificationCompletedEventListener {

    private final EmailVerificationService emailVerificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SignupEmailVerificationCompletedEvent event) {
        emailVerificationService.consumeSignupToken(
                event.email(),
                event.signupVerificationToken()
        );
    }
}
