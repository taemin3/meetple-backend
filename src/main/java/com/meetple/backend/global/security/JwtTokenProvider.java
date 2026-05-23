package com.meetple.backend.global.security;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProperties jwtProperties;

    public String createAccessToken(Member member) {
        return createToken(member, ACCESS_TOKEN_TYPE, jwtProperties.accessTokenExpirationSeconds());
    }

    public String createRefreshToken(Member member) {
        return createToken(member, REFRESH_TOKEN_TYPE, jwtProperties.refreshTokenExpirationSeconds());
    }

    private String createToken(Member member, String tokenType, long expirationSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim(EMAIL_CLAIM, member.getEmail())
                .claim(ROLE_CLAIM, member.getRole().name())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(jwtProperties.secretKey())
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, ACCESS_TOKEN_TYPE);
        MemberRole role = MemberRole.valueOf(claims.get(ROLE_CLAIM, String.class));
        AuthenticatedMember principal = new AuthenticatedMember(
                Long.valueOf(claims.getSubject()),
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
        Claims claims = parseClaims(token);
        validateTokenType(claims, REFRESH_TOKEN_TYPE);
        return Long.valueOf(claims.getSubject());
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
}
