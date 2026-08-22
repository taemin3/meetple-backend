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
import com.meetple.backend.domain.auth.config.PasswordResetProperties;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.request.PasswordResetRequest;
import com.meetple.backend.domain.auth.dto.response.PasswordResetVerificationResponse;
import com.meetple.backend.domain.auth.mail.EmailVerificationMailSender;
import com.meetple.backend.domain.auth.repository.PasswordResetRepository;
import com.meetple.backend.domain.auth.repository.PasswordResetRepository.CodeVerificationResult;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String EMAIL = "user@meetple.com";
    private static final String CODE = "123456";
    private static final String CODE_HASH = "code-hash";
    private static final String RESET_TOKEN = "reset-token";

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordResetRepository passwordResetRepository;
    @Mock
    private EmailVerificationMailSender emailVerificationMailSender;
    @Mock
    private EmailVerificationSecretGenerator secretGenerator;
    @Mock
    private EmailVerificationHasher hasher;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PushDeviceTokenService pushDeviceTokenService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EmailVerificationProperties emailVerificationProperties;
    private PasswordResetProperties passwordResetProperties;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        emailVerificationProperties = new EmailVerificationProperties(
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
        passwordResetProperties = new PasswordResetProperties(Duration.ofMinutes(15));
        passwordResetService = new PasswordResetService(
                memberRepository,
                passwordResetRepository,
                emailVerificationMailSender,
                secretGenerator,
                hasher,
                emailVerificationProperties,
                passwordResetProperties,
                passwordEncoder,
                refreshTokenRepository,
                pushDeviceTokenService,
                eventPublisher
        );
    }

    @Test
    void sendVerificationCodeSendsMailForExistingMember() {
        allowSend();
        given(secretGenerator.generateCode()).willReturn(CODE);
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(passwordResetRepository.saveChallengeIfAllowed(
                EMAIL,
                CODE_HASH,
                emailVerificationProperties.codeTtl(),
                emailVerificationProperties.resendCooldown()
        )).willReturn(true);
        given(memberRepository.existsByEmail(EMAIL)).willReturn(true);

        passwordResetService.sendVerificationCode(
                new EmailVerificationSendRequest(" USER@Meetple.com "),
                "127.0.0.1"
        );

        verify(emailVerificationMailSender).sendPasswordResetCode(
                EMAIL,
                CODE,
                emailVerificationProperties.codeTtl()
        );
    }

    @Test
    void sendVerificationCodeReturnsNormallyWithoutMailForUnknownEmail() {
        allowSend();
        given(secretGenerator.generateCode()).willReturn(CODE);
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(passwordResetRepository.saveChallengeIfAllowed(
                EMAIL,
                CODE_HASH,
                emailVerificationProperties.codeTtl(),
                emailVerificationProperties.resendCooldown()
        )).willReturn(true);
        given(memberRepository.existsByEmail(EMAIL)).willReturn(false);

        passwordResetService.sendVerificationCode(
                new EmailVerificationSendRequest(EMAIL),
                "127.0.0.1"
        );

        verify(emailVerificationMailSender, never())
                .sendPasswordResetCode(anyString(), anyString(), any());
    }

    @Test
    void sendVerificationCodeDeletesChallengeWhenMailFails() {
        allowSend();
        given(secretGenerator.generateCode()).willReturn(CODE);
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(passwordResetRepository.saveChallengeIfAllowed(
                EMAIL,
                CODE_HASH,
                emailVerificationProperties.codeTtl(),
                emailVerificationProperties.resendCooldown()
        )).willReturn(true);
        given(memberRepository.existsByEmail(EMAIL)).willReturn(true);
        doThrow(new IllegalStateException("mail failed"))
                .when(emailVerificationMailSender)
                .sendPasswordResetCode(EMAIL, CODE, emailVerificationProperties.codeTtl());

        assertThatThrownBy(() -> passwordResetService.sendVerificationCode(
                new EmailVerificationSendRequest(EMAIL),
                "127.0.0.1"
        )).isInstanceOf(IllegalStateException.class);

        verify(passwordResetRepository).deleteChallengeIfMatches(EMAIL, CODE_HASH);
    }

    @Test
    void confirmReturnsOneTimeResetToken() {
        allowConfirm();
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(secretGenerator.generateToken()).willReturn(RESET_TOKEN);
        given(passwordResetRepository.verifyCodeAndSaveResetToken(
                EMAIL,
                CODE_HASH,
                5,
                RESET_TOKEN,
                passwordResetProperties.tokenTtl()
        )).willReturn(CodeVerificationResult.VERIFIED);

        PasswordResetVerificationResponse response = passwordResetService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE),
                "127.0.0.1"
        );

        assertThat(response.passwordResetToken()).isEqualTo(RESET_TOKEN);
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void confirmRejectsInvalidCode() {
        allowConfirm();
        given(hasher.hashCode(EMAIL, CODE)).willReturn(CODE_HASH);
        given(secretGenerator.generateToken()).willReturn(RESET_TOKEN);
        given(passwordResetRepository.verifyCodeAndSaveResetToken(
                EMAIL,
                CODE_HASH,
                5,
                RESET_TOKEN,
                passwordResetProperties.tokenTtl()
        )).willReturn(CodeVerificationResult.INVALID);

        assertThatThrownBy(() -> passwordResetService.confirm(
                new EmailVerificationConfirmRequest(EMAIL, CODE),
                "127.0.0.1"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.EMAIL_VERIFICATION_CODE_INVALID.getMessage());
    }

    @Test
    void resetPasswordChangesPasswordAndInvalidatesAllSessions() {
        Member member = member();
        PasswordResetRequest request = request("new-password123");
        given(memberRepository.findByEmailForUpdate(EMAIL)).willReturn(Optional.of(member));
        given(passwordResetRepository.claimResetToken(RESET_TOKEN, EMAIL))
                .willReturn(Duration.ofMinutes(10));
        given(passwordEncoder.encode(request.newPassword())).willReturn("encoded-new-password");

        passwordResetService.resetPassword(request);

        assertThat(member.getPassword()).isEqualTo("encoded-new-password");
        verify(pushDeviceTokenService).removeAllDevices(1L);
        verify(refreshTokenRepository).deleteAllByMemberId(1L);
        ArgumentCaptor<ChatSessionInvalidationEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatSessionInvalidationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().memberId()).isEqualTo(1L);
    }

    @Test
    void resetPasswordRejectsInvalidTokenWithoutChangingPassword() {
        Member member = member();
        PasswordResetRequest request = request("new-password123");
        given(memberRepository.findByEmailForUpdate(EMAIL)).willReturn(Optional.of(member));
        given(passwordResetRepository.claimResetToken(RESET_TOKEN, EMAIL))
                .willReturn(Duration.ZERO);

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.PASSWORD_RESET_TOKEN_INVALID.getMessage());

        assertThat(member.getPassword()).isEqualTo("old-encoded-password");
        verify(refreshTokenRepository, never()).deleteAllByMemberId(any());
    }

    @Test
    void resetPasswordRestoresClaimWhenImmediateProcessingFailsWithoutTransactionProxy() {
        Member member = member();
        PasswordResetRequest request = request("new-password123");
        Duration remainingTtl = Duration.ofMinutes(10);
        given(memberRepository.findByEmailForUpdate(EMAIL)).willReturn(Optional.of(member));
        given(passwordResetRepository.claimResetToken(RESET_TOKEN, EMAIL))
                .willReturn(remainingTtl);
        given(passwordEncoder.encode(request.newPassword())).willReturn("encoded-new-password");
        doThrow(new IllegalStateException("redis failed"))
                .when(refreshTokenRepository).deleteAllByMemberId(1L);

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(IllegalStateException.class);

        verify(passwordResetRepository).restoreResetTokenIfNoNewerToken(
                RESET_TOKEN,
                EMAIL,
                remainingTtl
        );
    }

    @Test
    void resetPasswordRejectsPasswordOverBcryptByteLimitBeforeClaimingToken() {
        PasswordResetRequest request = request("가".repeat(25));

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");

        verify(memberRepository, never()).findByEmailForUpdate(anyString());
        verify(passwordResetRepository, never()).claimResetToken(anyString(), anyString());
    }

    private void allowSend() {
        given(passwordResetRepository.acquireSendPermit(
                "127.0.0.1",
                emailVerificationProperties.requesterRateLimitWindow(),
                emailVerificationProperties.requesterRateLimit(),
                emailVerificationProperties.globalRateLimitWindow(),
                emailVerificationProperties.globalRateLimit()
        )).willReturn(true);
    }

    private void allowConfirm() {
        given(passwordResetRepository.acquireConfirmPermit(
                "127.0.0.1",
                emailVerificationProperties.confirmationRequesterRateLimitWindow(),
                emailVerificationProperties.confirmationRequesterRateLimit()
        )).willReturn(true);
    }

    private Member member() {
        Member member = Member.createUser(EMAIL, "old-encoded-password", "tester", null);
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private PasswordResetRequest request(String password) {
        return new PasswordResetRequest(EMAIL, RESET_TOKEN, password);
    }
}
