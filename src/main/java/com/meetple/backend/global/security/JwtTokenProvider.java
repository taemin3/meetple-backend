package com.meetple.backend.global.security;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String EMAIL_CLAIM = "email";
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String SESSION_ID_CLAIM = "sid";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProperties jwtProperties;

    public String createAccessToken(Member member, String sessionId) {
        validateSessionId(sessionId);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.accessTokenExpirationSeconds());

        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim(EMAIL_CLAIM, member.getEmail())
                .claim(ROLE_CLAIM, member.getRole().name())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim(SESSION_ID_CLAIM, sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(jwtProperties.secretKey())
                .compact();
    }

    public String createRefreshToken(Member member, String sessionId) {
        validateSessionId(sessionId);
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.refreshTokenExpirationSeconds());

        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .claim(SESSION_ID_CLAIM, sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(jwtProperties.secretKey())
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, ACCESS_TOKEN_TYPE);
        JwtTokenSession tokenSession = toTokenSession(claims);
        MemberRole role = MemberRole.valueOf(claims.get(ROLE_CLAIM, String.class));
        AuthenticatedMember principal = new AuthenticatedMember(
                tokenSession.memberId(),
                claims.get(EMAIL_CLAIM, String.class),
                role
        );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()))
        );
    }

    public Long getRefreshTokenMemberId(String token) {
        return getRefreshTokenSession(token).memberId();
    }

    public Long getAccessTokenMemberId(String token) {
        return getAccessTokenSession(token).memberId();
    }

    public JwtTokenSession getRefreshTokenSession(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, REFRESH_TOKEN_TYPE);
        return toTokenSession(claims);
    }

    public JwtTokenSession getAccessTokenSession(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, ACCESS_TOKEN_TYPE);
        return toTokenSession(claims);
    }

    public Duration getAccessTokenRemainingExpiration(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, ACCESS_TOKEN_TYPE);
        Duration remainingExpiration = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (remainingExpiration.isNegative() || remainingExpiration.isZero()) {
            return Duration.ZERO;
        }
        return remainingExpiration;
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.accessTokenExpirationSeconds();
    }

    public long getRefreshTokenExpirationSeconds() {
        return jwtProperties.refreshTokenExpirationSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtProperties.secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void validateTokenType(Claims claims, String expectedTokenType) {
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedTokenType.equals(tokenType)) {
            throw new IllegalArgumentException("Invalid token type.");
        }
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Token session id is required.");
        }
    }

    private JwtTokenSession toTokenSession(Claims claims) {
        String sessionId = claims.get(SESSION_ID_CLAIM, String.class);
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Token session id is required.");
        }
        return new JwtTokenSession(Long.valueOf(claims.getSubject()), sessionId);
    }
}
