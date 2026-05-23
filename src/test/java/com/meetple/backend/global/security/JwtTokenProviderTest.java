package com.meetple.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import io.jsonwebtoken.JwtException;
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

        String accessToken = jwtTokenProvider.createAccessToken(member);
        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

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
    void createRefreshTokenAndReadMemberId() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);

        String refreshToken = jwtTokenProvider.createRefreshToken(member);

        assertThat(jwtTokenProvider.getRefreshTokenMemberId(refreshToken)).isEqualTo(1L);
    }

    @Test
    void getAuthenticationRejectsRefreshToken() {
        Member member = Member.createUser("user@meetple.com", "encoded-password", "tester", "Seoul");
        ReflectionTestUtils.setField(member, "id", 1L);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);

        assertThatThrownBy(() -> jwtTokenProvider.getAuthentication(refreshToken))
                .isInstanceOf(IllegalArgumentException.class);
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
}
