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
            new JwtProperties("test-jwt-secret-key-for-meetple-backend-1234567890", 3600)
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
}
