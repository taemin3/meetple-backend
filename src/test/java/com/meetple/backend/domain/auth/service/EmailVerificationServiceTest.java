package com.meetple.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.meetple.backend.domain.auth.mail.EmailVerificationMailSender;
import com.meetple.backend.domain.auth.repository.EmailVerificationRepository;
import com.meetple.backend.domain.auth.repository.EmailVerificationRepository.CodeVerificationResult;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.response.ErrorStatus;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "user@meetple.com";
    private static final String CODE = "123456";
    private static final String CODE_HASH = "code-hash";
    private static final String SIGNUP_TOKEN = "signup-token";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private EmailVerificationMailSender emailVerificationMailSender;

    @Mock
    private EmailVerificationSecretGenerator secretGenerator;

    @Mock
    private EmailVerificationHasher hasher;

    private EmailVerificationProperties properties;
    private EmailVerificationService emailVerificationService;

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
                "test-email-verification-secret-1234567890",
                "noreply@meetple.test"
        );
        emailVerificationService = new EmailVerificationService(
                memberRepository,
                emailVerificationRepository,
                emailVerificationMailSender,
                secretGenerator,
                hasher,
                properties
        );
    }

    @Test
    void sendVerificationCodeStoresChallengeAndSendsNormalizedEmail() {
        given(memberRepository.existsByEmail(EMAIL)).willReturn(false);
        allowSend();
        given(secretGenerator.generateCode()).willReturn(CODE);
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(emailVerificationRepository.saveChallengeIfAllowed(
                EMAIL,
                CODE_HASH,
                properties.codeTtl(),
                properties.resendCooldown()
        )).willReturn(true);

        emailVerificationService.sendVerificationCode(
                new EmailVerificationSendRequest("  USER@Meetple.com "),
                "127.0.0.1"
        );

        verify(emailVerificationMailSender).sendVerificationCode(
                EMAIL,
                CODE,
                properties.codeTtl()
        );
    }

    @Test
    void sendVerificationCodeRejectsExistingMember() {
        given(memberRepository.existsByEmail(EMAIL)).willReturn(true);

        assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(
                new EmailVerificationSendRequest(EMAIL),
                "127.0.0.1"
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorStatus.EMAIL_ALREADY_EXISTS.getMessage());

        verify(emailVerificationRepository, never()).saveChallengeIfAllowed(
                anyString(),
                anyString(),
                any(),
                any()
        );
    }

    @Test
    void sendVerificationCodeRejectsRequestDuringCooldown() {
        allowSend();
        given(secretGenerator.generateCode()).willReturn(CODE);
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(emailVerificationRepository.saveChallengeIfAllowed(
                EMAIL,
                CODE_HASH,
                properties.codeTtl(),
                properties.resendCooldown()
        )).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(
                new EmailVerificationSendRequest(EMAIL),
                "127.0.0.1"
        ))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_SEND_TOO_SOON.getMessage());

        verify(emailVerificationMailSender, never()).sendVerificationCode(any(), any(), any());
    }

    @Test
    void sendVerificationCodeDeletesChallengeWhenMailDeliveryFails() {
        allowSend();
        given(secretGenerator.generateCode()).willReturn(CODE);
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(emailVerificationRepository.saveChallengeIfAllowed(
                EMAIL,
                CODE_HASH,
                properties.codeTtl(),
                properties.resendCooldown()
        )).willReturn(true);
        doThrow(new BaseException(ErrorStatus.EXTERNAL_API_ERROR))
                .when(emailVerificationMailSender)
                .sendVerificationCode(EMAIL, CODE, properties.codeTtl());

        assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(
                new EmailVerificationSendRequest(EMAIL),
                "127.0.0.1"
        )).isInstanceOf(BaseException.class);

        verify(emailVerificationRepository).deleteChallengeIfMatches(EMAIL, CODE_HASH);
    }

    @Test
    void sendVerificationCodeRejectsRequesterOrGlobalRateLimit() {
        given(emailVerificationRepository.acquireSendPermit(
                "127.0.0.1",
                properties.requesterRateLimitWindow(),
                properties.requesterRateLimit(),
                properties.globalRateLimitWindow(),
                properties.globalRateLimit()
        )).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(
                new EmailVerificationSendRequest(EMAIL),
                "127.0.0.1"
        ))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_RATE_LIMITED.getMessage());

        verify(emailVerificationMailSender, never()).sendVerificationCode(any(), any(), any());
    }

    @Test
    void confirmReturnsSignupTokenForValidCode() {
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(emailVerificationRepository.verifyCode(EMAIL, CODE_HASH, 5))
                .willReturn(CodeVerificationResult.VERIFIED);
        given(secretGenerator.generateToken()).willReturn(SIGNUP_TOKEN);

        EmailVerificationConfirmResponse response = emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE)
        );

        assertThat(response.signupVerificationToken()).isEqualTo(SIGNUP_TOKEN);
        assertThat(response.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
        verify(emailVerificationRepository).saveSignupToken(
                SIGNUP_TOKEN,
                EMAIL,
                properties.signupTokenTtl()
        );
    }

    @Test
    void confirmRejectsInvalidCode() {
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(emailVerificationRepository.verifyCode(EMAIL, CODE_HASH, 5))
                .willReturn(CodeVerificationResult.INVALID);

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_CODE_INVALID.getMessage());
    }

    @Test
    void confirmRejectsExpiredCode() {
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(emailVerificationRepository.verifyCode(EMAIL, CODE_HASH, 5))
                .willReturn(CodeVerificationResult.EXPIRED);

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_CODE_EXPIRED.getMessage());
    }

    @Test
    void confirmRejectsAttemptsExceeded() {
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(emailVerificationRepository.verifyCode(EMAIL, CODE_HASH, 5))
                .willReturn(CodeVerificationResult.ATTEMPTS_EXCEEDED);

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE)
        ))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED.getMessage());
    }

    @Test
    void validateSignupTokenRejectsMissingOrMismatchedToken() {
        given(emailVerificationRepository.matchesSignupToken(SIGNUP_TOKEN, EMAIL))
                .willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.validateSignupToken(
                EMAIL,
                SIGNUP_TOKEN
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.SIGNUP_EMAIL_VERIFICATION_INVALID.getMessage());
    }

    private void allowSend() {
        given(emailVerificationRepository.acquireSendPermit(
                "127.0.0.1",
                properties.requesterRateLimitWindow(),
                properties.requesterRateLimit(),
                properties.globalRateLimitWindow(),
                properties.globalRateLimit()
        )).willReturn(true);
    }
}
