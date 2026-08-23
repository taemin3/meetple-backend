package com.meetple.backend.domain.auth.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final EmailVerificationSecretGenerator secretGenerator;
    private final EmailVerificationHasher hasher;
    private final EmailVerificationProperties properties;

    @Transactional
    public void sendVerificationCode(
            EmailVerificationSendRequest request,
            String requesterIdentifier
    ) {
        String email = EmailAddressNormalizer.normalize(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new ConflictException(ErrorStatus.EMAIL_ALREADY_EXISTS);
        }
        if (!emailVerificationRepository.acquireSendPermit(
                requesterIdentifier,
                properties.requesterRateLimitWindow(),
                properties.requesterRateLimit(),
                properties.globalRateLimitWindow(),
                properties.globalRateLimit()
        )) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_RATE_LIMITED);
        }

        String code = secretGenerator.generateCode();
        String codeHash = hasher.hashCode(email, code);
        boolean saved = emailVerificationRepository.saveChallengeIfAllowed(
                email,
                codeHash,
                properties.codeTtl(),
                properties.resendCooldown()
        );
        if (!saved) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_SEND_TOO_SOON);
        }

        emailDeliveryService.schedule(
                EmailDeliveryPurpose.SIGNUP_VERIFICATION,
                email,
                code,
                codeHash,
                true,
                properties.codeTtl()
        );
    }

    public EmailVerificationConfirmResponse confirm(
            EmailVerificationConfirmRequest request,
            String requesterIdentifier
    ) {
        String email = EmailAddressNormalizer.normalize(request.email());
        if (!emailVerificationRepository.acquireConfirmPermit(
                requesterIdentifier,
                properties.confirmationRequesterRateLimitWindow(),
                properties.confirmationRequesterRateLimit()
        )) {
            throw new BaseException(ErrorStatus.EMAIL_VERIFICATION_CONFIRM_RATE_LIMITED);
        }

        String codeHash = hasher.hashCode(email, request.code());
        String signupToken = secretGenerator.generateToken();
        CodeVerificationResult result = emailVerificationRepository
                .verifyCodeAndSaveSignupToken(
                        email,
                        codeHash,
                        properties.maxAttempts(),
                        signupToken,
                        properties.signupTokenTtl()
                );
        validateResult(result);

        return new EmailVerificationConfirmResponse(
                signupToken,
                properties.signupTokenTtl().toSeconds()
        );
    }

    public void validateSignupToken(String email, String signupVerificationToken) {
        String normalizedEmail = EmailAddressNormalizer.normalize(email);
        if (!emailVerificationRepository.matchesSignupToken(
                signupVerificationToken,
                normalizedEmail
        )) {
            throw new BadRequestException(ErrorStatus.SIGNUP_EMAIL_VERIFICATION_INVALID);
        }
    }

    public void consumeSignupToken(String email, String signupVerificationToken) {
        emailVerificationRepository.consumeSignupToken(
                signupVerificationToken,
                EmailAddressNormalizer.normalize(email)
        );
    }

    private void validateResult(CodeVerificationResult result) {
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
}
