package com.meetple.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            new JwtProperties("test-jwt-secret-key-for-meetple-backend-1234567890", 3600, 1209600)
    );

    @Test
    void createAccessTokenAndReadAuthentication() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);

        String accessToken = jwtTokenProvider.createAccessToken(member, "session-id");
        AuthenticatedAccessToken authenticatedToken = jwtTokenProvider.authenticateAccessToken(accessToken);
        Authentication authentication = authenticatedToken.authentication();

        assertThat(jwtTokenProvider.getAccessTokenMemberId(accessToken)).isEqualTo(1L);
        assertThat(authenticatedToken.session())
                .isEqualTo(new JwtTokenSession(1L, "session-id"));
        assertThat(jwtTokenProvider.getAccessTokenRemainingExpiration(accessToken))
                .isGreaterThan(Duration.ZERO);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(
                new AuthenticatedMember(1L, "user@meetple.com", MemberRole.USER)
        );
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    void getAuthenticationRejectsInvalidToken() {
        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication("invalid-token"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void authenticatesAccessTokenConcurrentlyWithSharedParser() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);
        String accessToken = jwtTokenProvider.createAccessToken(member, "session-id");

        List<AuthenticatedAccessToken> authenticatedTokens = IntStream.range(0, 100)
                .parallel()
                .mapToObj(index -> jwtTokenProvider.authenticateAccessToken(accessToken))
                .toList();

        assertThat(authenticatedTokens)
                .hasSize(100)
                .allSatisfy(authenticatedToken -> assertThat(authenticatedToken.session())
                        .isEqualTo(new JwtTokenSession(1L, "session-id")));
    }

    @Test
    void createAccessTokenRejectsBlankSessionId() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);

        assertThatThrownBy(() -> jwtTokenProvider.createAccessToken(member, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token session id is required.");
    }

    @Test
    void createRefreshTokenAndReadSession() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);

        String refreshToken = jwtTokenProvider.createRefreshToken(member, "session-id");

        assertThat(jwtTokenProvider.getRefreshTokenMemberId(refreshToken)).isEqualTo(1L);
        assertThat(jwtTokenProvider.getRefreshTokenSession(refreshToken))
                .isEqualTo(new JwtTokenSession(1L, "session-id"));
    }

    @Test
    void createRefreshTokenRejectsBlankSessionId() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);

        assertThatThrownBy(() -> jwtTokenProvider.createRefreshToken(member, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token session id is required.");
    }

    @Test
    void refreshTokenContainsOnlyMinimalClaims() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);

        String refreshToken = jwtTokenProvider.createRefreshToken(member, "session-id");
        Claims claims = parseClaims(refreshToken);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("tokenType", String.class)).isEqualTo("refresh");
        assertThat(claims.get("sid", String.class)).isEqualTo("session-id");
        assertThat(claims.get("email")).isNull();
        assertThat(claims.get("role")).isNull();
    }

    @Test
    void getAuthenticationRejectsRefreshToken() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);
        String refreshToken = jwtTokenProvider.createRefreshToken(member, "session-id");

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(refreshToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getRefreshTokenSessionRejectsTokenWithoutSessionId() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);
        String refreshToken = Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim("tokenType", "refresh")
                .signWith(new JwtProperties(
                        "test-jwt-secret-key-for-meetple-backend-1234567890",
                        3600,
                        1209600
                ).secretKey())
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.getRefreshTokenSession(refreshToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token session id is required.");
    }

    @Test
    void jwtPropertiesRejectsWeakSecret() {
        assertThatThrownBy(() -> new JwtProperties("short-secret", 3600, 1209600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void jwtPropertiesRejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new JwtProperties("test-jwt-secret-key-for-meetple-backend-1234567890", 0, 1209600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void jwtPropertiesRejectsNonPositiveRefreshExpiration() {
        assertThatThrownBy(() -> new JwtProperties("test-jwt-secret-key-for-meetple-backend-1234567890", 3600, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refresh-token-expiration-seconds");
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(new JwtProperties(
                        "test-jwt-secret-key-for-meetple-backend-1234567890",
                        3600,
                        1209600
                ).secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
