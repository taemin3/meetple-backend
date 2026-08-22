package com.meetple.backend.domain.auth.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.mail.EmailVerificationMailSender;
import com.meetple.backend.domain.auth.repository.PasswordResetRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailRequestedEventListenerTest {

    @Mock
    private EmailVerificationMailSender mailSender;
    @Mock
    private PasswordResetRepository passwordResetRepository;

    private EmailVerificationProperties properties;
    private PasswordResetMailRequestedEventListener listener;

    @BeforeEach
    void setUp() {
        properties = new EmailVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ofMinutes(1),
                Duration.ofMinutes(15),
                5,
                Duration.ofMinutes(1),
                5,
                Duration.ofMinutes(1),
                100,
                Duration.ofMinutes(1),
                10,
                "test-email-verification-secret-1234567890",
                "noreply@meetple.test"
        );
        listener = new PasswordResetMailRequestedEventListener(
                mailSender,
                passwordResetRepository,
                properties
        );
    }

    @Test
    void sendsMailOnlyForExistingMemberEvent() {
        listener.handle(new PasswordResetMailRequestedEvent(
                "user@meetple.com",
                "123456",
                "code-hash",
                true
        ));

        verify(mailSender).sendPasswordResetCode(
                "user@meetple.com",
                "123456",
                properties.codeTtl()
        );
    }

    @Test
    void skipsMailForUnknownMemberEvent() {
        listener.handle(new PasswordResetMailRequestedEvent(
                "unknown@meetple.com",
                "123456",
                "code-hash",
                false
        ));

        verify(mailSender, never()).sendPasswordResetCode(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void deletesMatchingChallengeWhenAsynchronousMailFails() {
        doThrow(new IllegalStateException("mail failed"))
                .when(mailSender)
                .sendPasswordResetCode(
                        "user@meetple.com",
                        "123456",
                        properties.codeTtl()
                );

        listener.handle(new PasswordResetMailRequestedEvent(
                "user@meetple.com",
                "123456",
                "code-hash",
                true
        ));

        verify(passwordResetRepository).deleteChallengeIfMatches(
                "user@meetple.com",
                "code-hash"
        );
    }
}
