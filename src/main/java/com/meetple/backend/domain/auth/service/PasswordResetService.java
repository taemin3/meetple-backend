package com.meetple.backend.domain.auth.service;

import com.meetple.backend.domain.auth.config.EmailVerificationProperties;
import com.meetple.backend.domain.auth.config.PasswordResetProperties;
import com.meetple.backend.domain.auth.email.EmailDeliveryPurpose;
import com.meetple.backend.domain.auth.email.EmailDeliveryService;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.request.PasswordResetRequest;
import com.meetple.backend.domain.auth.dto.response.PasswordResetVerificationResponse;
import com.meetple.backend.domain.auth.repository.PasswordResetRepository;
import com.meetple.backend.domain.auth.repository.PasswordResetRepository.CodeVerificationResult;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String PASSWORD_TOO_LONG_MESSAGE = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.";
    private static final String PASSWORD_COMPOSITION_MESSAGE = "비밀번호는 영문과 숫자를 포함해야 합니다.";
    private static final Pattern PASSWORD_LETTER_PATTERN = Pattern.compile("[A-Za-z]");
    private static final Pattern PASSWORD_DIGIT_PATTERN = Pattern.compile("\\d");

    private final MemberRepository memberRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final EmailVerificationSecretGenerator secretGenerator;
    private final EmailVerificationHasher hasher;
    private final EmailVerificationProperties emailVerificationProperties;
    private final PasswordResetProperties passwordResetProperties;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PushDeviceTokenService pushDeviceTokenService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void sendVerificationCode(
            EmailVerificationSendRequest request,
            String requesterIdentifier
    ) {
        String email = EmailAddressNormalizer.normalize(request.email());
        if (!passwordResetRepository.acquireSendPermit(
                requesterIdentifier,
                emailVerificationProperties.requesterRateLimitWindow(),
                emailVerificationProperties.requesterRateLimit(),
                emailVerificationProperties.globalRateLimitWindow(),
                emailVerificationProperties.globalRateLimit()
        )) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_RATE_LIMITED);
        }

        String code = secretGenerator.generateCode();
        String codeHash = hasher.hashCode(email, code);
        boolean saved = passwordResetRepository.saveChallengeIfAllowed(
                email,
                codeHash,
                emailVerificationProperties.codeTtl(),
                emailVerificationProperties.resendCooldown()
        );
        if (!saved) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_SEND_TOO_SOON);
        }

        emailDeliveryService.schedule(
                EmailDeliveryPurpose.PASSWORD_RESET,
                email,
                code,
                codeHash,
                memberRepository.existsByEmail(email),
                emailVerificationProperties.codeTtl()
        );
    }

    public PasswordResetVerificationResponse confirm(
            EmailVerificationConfirmRequest request,
            String requesterIdentifier
    ) {
        String email = EmailAddressNormalizer.normalize(request.email());
        if (!passwordResetRepository.acquireConfirmPermit(
                requesterIdentifier,
                emailVerificationProperties.confirmationRequesterRateLimitWindow(),
                emailVerificationProperties.confirmationRequesterRateLimit()
        )) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_CONFIRM_RATE_LIMITED);
        }

        String codeHash = hasher.hashCode(email, request.code());
        String resetToken = secretGenerator.generateToken();
        CodeVerificationResult result = passwordResetRepository.verifyCodeAndSaveResetToken(
                email,
                codeHash,
                emailVerificationProperties.maxAttempts(),
                resetToken,
                passwordResetProperties.tokenTtl(),
                requesterIdentifier
        );
        validateVerificationResult(result);

        return new PasswordResetVerificationResponse(
                resetToken,
                passwordResetProperties.tokenTtl().toSeconds()
        );
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        validateNewPassword(request.newPassword());
        String email = EmailAddressNormalizer.normalize(request.email());
        Instant tokenClaimStartedAt = Instant.now();
        Duration remainingTtl = passwordResetRepository.claimResetToken(
                request.passwordResetToken(),
                email
        );
        if (remainingTtl.isZero() || remainingTtl.isNegative()) {
            throw new BadRequestException(ErrorStatus.PASSWORD_RESET_TOKEN_INVALID);
        }
        Instant tokenExpiresAt = tokenClaimStartedAt.plus(remainingTtl);

        boolean transactionSynchronizationActive =
                TransactionSynchronizationManager.isSynchronizationActive();
        if (transactionSynchronizationActive) {
            registerRollbackRestore(request, email, tokenExpiresAt);
        }

        try {
            Member member = memberRepository.findByEmailForUpdate(email)
                    .orElseThrow(() -> new BadRequestException(
                            ErrorStatus.PASSWORD_RESET_TOKEN_INVALID
                    ));
            if (!Instant.now().isBefore(tokenExpiresAt)) {
                throw new BadRequestException(ErrorStatus.PASSWORD_RESET_TOKEN_INVALID);
            }
            member.changePassword(passwordEncoder.encode(request.newPassword()));
            pushDeviceTokenService.removeAllDevices(member.getId());
            refreshTokenRepository.deleteAllByMemberId(member.getId());
            eventPublisher.publishEvent(ChatSessionInvalidationEvent.member(member.getId()));
        } catch (RuntimeException exception) {
            if (!transactionSynchronizationActive) {
                restoreClaimQuietly(request.passwordResetToken(), email, tokenExpiresAt);
            }
            throw exception;
        }
    }

    private void registerRollbackRestore(
            PasswordResetRequest request,
            String email,
            Instant tokenExpiresAt
    ) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    restoreClaimQuietly(request.passwordResetToken(), email, tokenExpiresAt);
                }
            }
        });
    }

    private void restoreClaimQuietly(String token, String email, Instant tokenExpiresAt) {
        Duration actualRemainingTtl = Duration.between(Instant.now(), tokenExpiresAt);
        if (actualRemainingTtl.isZero() || actualRemainingTtl.isNegative()) {
            return;
        }
        try {
            passwordResetRepository.restoreResetTokenIfNoNewerToken(
                    token,
                    email,
                    actualRemainingTtl
            );
        } catch (RuntimeException restoreException) {
            log.warn("Failed to restore password reset token after rollback", restoreException);
        }
    }

    private void validateVerificationResult(CodeVerificationResult result) {
        switch (result) {
            case VERIFIED -> {
                return;
            }
            case INVALID -> throw new BadRequestException(
                    ErrorStatus.EMAIL_VERIFICATION_CODE_INVALID
            );
            case EXPIRED -> throw new BadRequestException(
                    ErrorStatus.EMAIL_VERIFICATION_CODE_EXPIRED
            );
            case ATTEMPTS_EXCEEDED -> throw new BaseException(
                    ErrorStatus.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED
            );
        }
    }

    private void validatePasswordByteLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BadRequestException(PASSWORD_TOO_LONG_MESSAGE);
        }
    }

    private void validateNewPassword(String password) {
        validatePasswordByteLength(password);
        if (!PASSWORD_LETTER_PATTERN.matcher(password).find()
                || !PASSWORD_DIGIT_PATTERN.matcher(password).find()) {
            throw new BadRequestException(PASSWORD_COMPOSITION_MESSAGE);
        }
    }
}
