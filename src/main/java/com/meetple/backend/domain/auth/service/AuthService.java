package com.meetple.backend.domain.auth.service;

import com.meetple.backend.domain.auth.dto.request.LoginRequest;
import com.meetple.backend.domain.auth.dto.request.LogoutRequest;
import com.meetple.backend.domain.auth.dto.request.ReissueRequest;
import com.meetple.backend.domain.auth.dto.request.SignupRequest;
import com.meetple.backend.domain.auth.dto.response.AuthMemberResponse;
import com.meetple.backend.domain.auth.dto.response.LoginResponse;
import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.legal.entity.LegalDocument;
import com.meetple.backend.domain.legal.service.LegalDocumentService;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.UnauthorizedException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.JwtTokenProvider;
import com.meetple.backend.global.security.JwtTokenSession;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_LOGIN_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "유효하지 않은 refresh token입니다.";
    private static final String INVALID_ACCESS_TOKEN_MESSAGE = "유효하지 않은 access token입니다.";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String PASSWORD_TOO_LONG_MESSAGE = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.";
    private static final String PASSWORD_COMPOSITION_MESSAGE = "비밀번호는 영문과 숫자를 포함해야 합니다.";
    private static final Pattern PASSWORD_LETTER_PATTERN = Pattern.compile("[A-Za-z]");
    private static final Pattern PASSWORD_DIGIT_PATTERN = Pattern.compile("\\d");

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;
    private final PushDeviceTokenService pushDeviceTokenService;
    private final LegalDocumentService legalDocumentService;
    private final EmailVerificationService emailVerificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AuthMemberResponse signup(SignupRequest request) {
        validateNewPassword(request.password());
        String email = EmailAddressNormalizer.normalize(request.email());
        List<LegalDocument> legalDocuments = legalDocumentService.resolveCurrentSignupDocuments(
                request.legalDocuments()
        );

        if (memberRepository.existsByEmail(email)) {
            throw new ConflictException(ErrorStatus.EMAIL_ALREADY_EXISTS);
        }
        emailVerificationService.validateSignupToken(email, request.signupVerificationToken());

        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = Member.createUser(email, encodedPassword, request.nickname(), null);
        Member savedMember = saveMember(member);
        legalDocumentService.recordSignup(savedMember, legalDocuments);
        eventPublisher.publishEvent(new SignupEmailVerificationCompletedEvent(
                email,
                request.signupVerificationToken()
        ));

        return AuthMemberResponse.from(savedMember);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        validatePasswordByteLength(request.password());
        String email = EmailAddressNormalizer.normalize(request.email());

        Member member = memberRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new UnauthorizedException(INVALID_LOGIN_MESSAGE));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new UnauthorizedException(INVALID_LOGIN_MESSAGE);
        }

        return issueTokens(member);
    }

    @Transactional
    public LoginResponse reissue(ReissueRequest request) {
        JwtTokenSession refreshTokenSession = parseRefreshTokenSession(request.refreshToken());
        Long memberId = refreshTokenSession.memberId();
        String sessionId = refreshTokenSession.sessionId();

        if (!refreshTokenRepository.matches(memberId, sessionId, request.refreshToken())) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));

        if (!refreshTokenRepository.matches(memberId, sessionId, request.refreshToken())) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        return issueTokens(member, sessionId);
    }

    public void logout(LogoutRequest request, String authorizationHeader) {
        JwtTokenSession refreshTokenSession = parseRefreshTokenSession(request.refreshToken());
        String accessToken = resolveAccessToken(authorizationHeader);
        JwtTokenSession accessTokenSession = parseAccessTokenSession(accessToken);

        if (!refreshTokenSession.equals(accessTokenSession)) {
            throw new UnauthorizedException(INVALID_ACCESS_TOKEN_MESSAGE);
        }

        if (!refreshTokenRepository.matches(
                refreshTokenSession.memberId(),
                refreshTokenSession.sessionId(),
                request.refreshToken()
        )) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        if (StringUtils.hasText(request.deviceId())) {
            pushDeviceTokenService.removeDevice(refreshTokenSession.memberId(), request.deviceId());
        }
        accessTokenBlacklistRepository.save(
                accessToken,
                jwtTokenProvider.getAccessTokenRemainingExpiration(accessToken)
        );
        refreshTokenRepository.deleteByMemberIdAndSessionId(
                refreshTokenSession.memberId(),
                refreshTokenSession.sessionId()
        );
        eventPublisher.publishEvent(ChatSessionInvalidationEvent.loginSession(
                refreshTokenSession.memberId(),
                refreshTokenSession.sessionId()
        ));
    }

    public void logoutAll(String authorizationHeader) {
        String accessToken = resolveAccessToken(authorizationHeader);
        JwtTokenSession accessTokenSession = parseAccessTokenSession(accessToken);

        pushDeviceTokenService.removeAllDevices(accessTokenSession.memberId());
        refreshTokenRepository.deleteAllByMemberId(accessTokenSession.memberId());
        eventPublisher.publishEvent(
                ChatSessionInvalidationEvent.member(accessTokenSession.memberId())
        );
    }

    private Member saveMember(Member member) {
        try {
            return memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ErrorStatus.EMAIL_ALREADY_EXISTS);
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

    private LoginResponse issueTokens(Member member) {
        return issueTokens(member, UUID.randomUUID().toString());
    }

    private LoginResponse issueTokens(Member member, String sessionId) {
        String accessToken = jwtTokenProvider.createAccessToken(member, sessionId);
        String refreshToken = jwtTokenProvider.createRefreshToken(member, sessionId);
        long refreshTokenExpirationSeconds = jwtTokenProvider.getRefreshTokenExpirationSeconds();

        refreshTokenRepository.save(
                member.getId(),
                sessionId,
                refreshToken,
                Duration.ofSeconds(refreshTokenExpirationSeconds)
        );

        return LoginResponse.bearer(
                accessToken,
                refreshToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                refreshTokenExpirationSeconds
        );
    }

    private JwtTokenSession parseRefreshTokenSession(String refreshToken) {
        try {
            return jwtTokenProvider.getRefreshTokenSession(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
    }

    private JwtTokenSession parseAccessTokenSession(String accessToken) {
        try {
            return jwtTokenProvider.getAccessTokenSession(accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException(INVALID_ACCESS_TOKEN_MESSAGE);
        }
    }

    private String resolveAccessToken(String authorizationHeader) {
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        throw new UnauthorizedException(INVALID_ACCESS_TOKEN_MESSAGE);
    }
}
