package com.meetple.backend.domain.auth.service;

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
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    private final MemberRepository memberRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailVerificationMailSender emailVerificationMailSender;
    private final EmailVerificationSecretGenerator secretGenerator;
    private final EmailVerificationHasher hasher;
    private final EmailVerificationProperties emailVerificationProperties;
    private final PasswordResetProperties passwordResetProperties;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PushDeviceTokenService pushDeviceTokenService;
    private final ApplicationEventPublisher eventPublisher;

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

        if (!memberRepository.existsByEmail(email)) {
            return;
        }

        try {
            emailVerificationMailSender.sendPasswordResetCode(
                    email,
                    code,
                    emailVerificationProperties.codeTtl()
            );
        } catch (RuntimeException exception) {
            passwordResetRepository.deleteChallengeIfMatches(email, codeHash);
            throw exception;
        }
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
                passwordResetProperties.tokenTtl()
        );
        validateVerificationResult(result);

        return new PasswordResetVerificationResponse(
                resetToken,
                passwordResetProperties.tokenTtl().toSeconds()
        );
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        validatePasswordByteLength(request.newPassword());
        String email = EmailAddressNormalizer.normalize(request.email());
        Member member = memberRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BadRequestException(ErrorStatus.PASSWORD_RESET_TOKEN_INVALID));

        Duration remainingTtl = passwordResetRepository.claimResetToken(
                request.passwordResetToken(),
                email
        );
        if (remainingTtl.isZero() || remainingTtl.isNegative()) {
            throw new BadRequestException(ErrorStatus.PASSWORD_RESET_TOKEN_INVALID);
        }

        boolean transactionSynchronizationActive =
                TransactionSynchronizationManager.isSynchronizationActive();
        if (transactionSynchronizationActive) {
            registerRollbackRestore(request, email, remainingTtl);
        }

        try {
            member.changePassword(passwordEncoder.encode(request.newPassword()));
            pushDeviceTokenService.removeAllDevices(member.getId());
            refreshTokenRepository.deleteAllByMemberId(member.getId());
            eventPublisher.publishEvent(ChatSessionInvalidationEvent.member(member.getId()));
        } catch (RuntimeException exception) {
            if (!transactionSynchronizationActive) {
                restoreClaimQuietly(request.passwordResetToken(), email, remainingTtl);
            }
            throw exception;
        }
    }

    private void registerRollbackRestore(
            PasswordResetRequest request,
            String email,
            Duration remainingTtl
    ) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    restoreClaimQuietly(request.passwordResetToken(), email, remainingTtl);
                }
            }
        });
    }

    private void restoreClaimQuietly(String token, String email, Duration remainingTtl) {
        try {
            passwordResetRepository.restoreResetTokenIfNoNewerToken(
                    token,
                    email,
                    remainingTtl
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
}
