package com.meetple.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.meetple.backend.domain.auth.email.EmailDeliveryPurpose;
import com.meetple.backend.domain.auth.email.EmailDeliveryService;
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
    private EmailDeliveryService emailDeliveryService;

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
                Duration.ofMinutes(1),
                10,
                "test-email-verification-secret-1234567890",
                "noreply@meetple.test"
        );
        emailVerificationService = new EmailVerificationService(
                memberRepository,
                emailVerificationRepository,
                emailDeliveryService,
                secretGenerator,
                hasher,
                properties
        );
    }

    @Test
    void sendVerificationCodeStoresChallengeAndSchedulesNormalizedEmail() {
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

        verify(emailDeliveryService).schedule(
                EmailDeliveryPurpose.SIGNUP_VERIFICATION,
                EMAIL,
                CODE,
                CODE_HASH,
                true,
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

        verify(emailDeliveryService, never()).schedule(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void sendVerificationCodePropagatesSchedulingFailure() {
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
                .when(emailDeliveryService)
                .schedule(
                        EmailDeliveryPurpose.SIGNUP_VERIFICATION,
                        EMAIL,
                        CODE,
                        CODE_HASH,
                        true,
                        properties.codeTtl()
                );

        assertThatThrownBy(() -> emailVerificationService.sendVerificationCode(
                new EmailVerificationSendRequest(EMAIL),
                "127.0.0.1"
        )).isInstanceOf(BaseException.class);

        verify(emailDeliveryService).schedule(
                EmailDeliveryPurpose.SIGNUP_VERIFICATION,
                EMAIL,
                CODE,
                CODE_HASH,
                true,
                properties.codeTtl()
        );
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

        verify(emailDeliveryService, never()).schedule(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void confirmReturnsSignupTokenForValidCode() {
        allowConfirm();
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(secretGenerator.generateToken()).willReturn(SIGNUP_TOKEN);
        given(emailVerificationRepository.verifyCodeAndSaveSignupToken(
                EMAIL,
                CODE_HASH,
                5,
                SIGNUP_TOKEN,
                properties.signupTokenTtl()
        )).willReturn(CodeVerificationResult.VERIFIED);

        EmailVerificationConfirmResponse response = emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE),
                "127.0.0.1"
        );

        assertThat(response.signupVerificationToken()).isEqualTo(SIGNUP_TOKEN);
        assertThat(response.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
        verify(emailVerificationRepository).verifyCodeAndSaveSignupToken(
                EMAIL,
                CODE_HASH,
                5,
                SIGNUP_TOKEN,
                properties.signupTokenTtl()
        );
    }

    @Test
    void confirmRejectsInvalidCode() {
        allowConfirm();
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(secretGenerator.generateToken()).willReturn(SIGNUP_TOKEN);
        given(emailVerificationRepository.verifyCodeAndSaveSignupToken(
                EMAIL,
                CODE_HASH,
                5,
                SIGNUP_TOKEN,
                properties.signupTokenTtl()
        ))
                .willReturn(CodeVerificationResult.INVALID);

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE),
                "127.0.0.1"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_CODE_INVALID.getMessage());
    }

    @Test
    void confirmRejectsExpiredCode() {
        allowConfirm();
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(secretGenerator.generateToken()).willReturn(SIGNUP_TOKEN);
        given(emailVerificationRepository.verifyCodeAndSaveSignupToken(
                EMAIL,
                CODE_HASH,
                5,
                SIGNUP_TOKEN,
                properties.signupTokenTtl()
        ))
                .willReturn(CodeVerificationResult.EXPIRED);

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE),
                "127.0.0.1"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_CODE_EXPIRED.getMessage());
    }

    @Test
    void confirmRejectsAttemptsExceeded() {
        allowConfirm();
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(secretGenerator.generateToken()).willReturn(SIGNUP_TOKEN);
        given(emailVerificationRepository.verifyCodeAndSaveSignupToken(
                EMAIL,
                CODE_HASH,
                5,
                SIGNUP_TOKEN,
                properties.signupTokenTtl()
        ))
                .willReturn(CodeVerificationResult.ATTEMPTS_EXCEEDED);

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE),
                "127.0.0.1"
        ))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED.getMessage());
    }

    @Test
    void confirmRejectsRequesterRateLimit() {
        given(emailVerificationRepository.acquireConfirmPermit(
                "127.0.0.1",
                properties.confirmationRequesterRateLimitWindow(),
                properties.confirmationRequesterRateLimit()
        )).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE),
                "127.0.0.1"
        ))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_CONFIRM_RATE_LIMITED.getMessage());

        verify(hasher, never()).hashCode(anyString(), anyString());
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

    private void allowConfirm() {
        given(emailVerificationRepository.acquireConfirmPermit(
                "127.0.0.1",
                properties.confirmationRequesterRateLimitWindow(),
                properties.confirmationRequesterRateLimit()
        )).willReturn(true);
    }
}
