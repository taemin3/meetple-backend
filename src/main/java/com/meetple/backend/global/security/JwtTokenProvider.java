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
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProperties jwtProperties;

    public String createAccessToken(Member member) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.accessTokenExpirationSeconds());

        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim(EMAIL_CLAIM, member.getEmail())
                .claim(ROLE_CLAIM, member.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(jwtProperties.secretKey())
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
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

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.accessTokenExpirationSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtProperties.secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
