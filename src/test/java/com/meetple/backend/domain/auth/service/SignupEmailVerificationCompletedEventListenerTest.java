package com.meetple.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignupEmailVerificationCompletedEventListenerTest {

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private SignupEmailVerificationCompletedEventListener listener;

    @Test
    void consumesSignupTokenAfterCommittedSignupEvent() {
        SignupEmailVerificationCompletedEvent event =
                new SignupEmailVerificationCompletedEvent(
                        "user@meetple.com",
                        "signup-token"
                );

        listener.handle(event);

        verify(emailVerificationService).consumeSignupToken(
                event.email(),
                event.signupVerificationToken()
        );
    }

    @Test
    void doesNotPropagateTokenCleanupFailureAfterCommit() {
        SignupEmailVerificationCompletedEvent event =
                new SignupEmailVerificationCompletedEvent(
                        "user@meetple.com",
                        "signup-token"
                );
        doThrow(new IllegalStateException("redis unavailable"))
                .when(emailVerificationService)
                .consumeSignupToken(event.email(), event.signupVerificationToken());

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
    }
}
