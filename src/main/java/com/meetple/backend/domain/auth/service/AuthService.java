package com.meetple.backend.domain.auth.service;

import com.meetple.backend.domain.auth.dto.request.LoginRequest;
import com.meetple.backend.domain.auth.dto.request.LogoutRequest;
import com.meetple.backend.domain.auth.dto.request.ReissueRequest;
import com.meetple.backend.domain.auth.dto.request.SignupRequest;
import com.meetple.backend.domain.auth.dto.response.AuthMemberResponse;
import com.meetple.backend.domain.auth.dto.response.LoginResponse;
import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.UnauthorizedException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_LOGIN_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "유효하지 않은 refresh token입니다.";
    private static final String INVALID_ACCESS_TOKEN_MESSAGE = "유효하지 않은 access token입니다.";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String PASSWORD_TOO_LONG_MESSAGE = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    @Transactional
    public AuthMemberResponse signup(SignupRequest request) {
        validatePasswordByteLength(request.password());

        if (memberRepository.existsByEmail(request.email())) {
            throw new ConflictException(ErrorStatus.EMAIL_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = Member.createUser(request.email(), encodedPassword, request.nickname(), null);

        return AuthMemberResponse.from(saveMember(member));
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        validatePasswordByteLength(request.password());

        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException(INVALID_LOGIN_MESSAGE));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new UnauthorizedException(INVALID_LOGIN_MESSAGE);
        }

        return issueTokens(member);
    }

    @Transactional
    public LoginResponse reissue(ReissueRequest request) {
        Long memberId = parseRefreshTokenMemberId(request.refreshToken());

        if (!refreshTokenRepository.matches(memberId, request.refreshToken())) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));

        return issueTokens(member);
    }

    public void logout(LogoutRequest request, String authorizationHeader) {
        Long memberId = parseRefreshTokenMemberId(request.refreshToken());
        String accessToken = resolveAccessToken(authorizationHeader);
        Long accessTokenMemberId = parseAccessTokenMemberId(accessToken);

        if (!memberId.equals(accessTokenMemberId)) {
            throw new UnauthorizedException(INVALID_ACCESS_TOKEN_MESSAGE);
        }

        if (!refreshTokenRepository.matches(memberId, request.refreshToken())) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        accessTokenBlacklistRepository.save(
                accessToken,
                jwtTokenProvider.getAccessTokenRemainingExpiration(accessToken)
        );
        refreshTokenRepository.deleteByMemberId(memberId);
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

    private LoginResponse issueTokens(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);
        long refreshTokenExpirationSeconds = jwtTokenProvider.getRefreshTokenExpirationSeconds();

        refreshTokenRepository.save(
                member.getId(),
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

    private Long parseRefreshTokenMemberId(String refreshToken) {
        try {
            return jwtTokenProvider.getRefreshTokenMemberId(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
    }

    private Long parseAccessTokenMemberId(String accessToken) {
        try {
            return jwtTokenProvider.getAccessTokenMemberId(accessToken);
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
