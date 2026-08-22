package com.meetple.backend.domain.auth.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationMailSender emailVerificationMailSender;
    private final EmailVerificationSecretGenerator secretGenerator;
    private final EmailVerificationHasher hasher;
    private final EmailVerificationProperties properties;

    public void sendVerificationCode(EmailVerificationSendRequest request) {
        String email = EmailAddressNormalizer.normalize(request.email());
        if (memberRepository.existsByEmail(email)) {
            throw new ConflictException(ErrorStatus.EMAIL_ALREADY_EXISTS);
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

        try {
            emailVerificationMailSender.sendVerificationCode(email, code, properties.codeTtl());
        } catch (RuntimeException e) {
            emailVerificationRepository.deleteChallenge(email);
            throw e;
        }
    }

    public EmailVerificationConfirmResponse confirm(EmailVerificationConfirmRequest request) {
        String email = EmailAddressNormalizer.normalize(request.email());
        String codeHash = hasher.hashCode(email, request.code());
        CodeVerificationResult result = emailVerificationRepository.verifyCode(
                email,
                codeHash,
                properties.maxAttempts()
        );
        validateResult(result);

        String signupToken = secretGenerator.generateToken();
        emailVerificationRepository.saveSignupToken(
                signupToken,
                email,
                properties.signupTokenTtl()
        );
        return new EmailVerificationConfirmResponse(
                signupToken,
                properties.signupTokenTtl().toSeconds()
        );
    }

    public void consumeSignupToken(String email, String signupVerificationToken) {
        String normalizedEmail = EmailAddressNormalizer.normalize(email);
        if (!emailVerificationRepository.consumeSignupToken(
                signupVerificationToken,
                normalizedEmail
        )) {
            throw new BadRequestException(ErrorStatus.SIGNUP_EMAIL_VERIFICATION_INVALID);
        }
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
