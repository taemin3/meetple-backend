package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void saveStoresHashedRefreshTokenWithTtl() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

        repository.save(1L, "session-id", "refresh-token", Duration.ofDays(14));

        String hashedRefreshToken = sha256("refresh-token");
        verify(valueOperations).set("refresh:1:session-id", hashedRefreshToken, Duration.ofDays(14));
        assertThat(hashedRefreshToken).doesNotContain("refresh-token");
    }

    @Test
    void matchesReturnsTrueWhenStoredHashMatchesRefreshToken() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:1:session-id")).willReturn(sha256("refresh-token"));

        boolean matches = repository.matches(1L, "session-id", "refresh-token");

        assertThat(matches).isTrue();
    }

    @Test
    void matchesReturnsFalseWhenStoredHashDoesNotMatchRefreshToken() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:1:session-id")).willReturn(sha256("another-refresh-token"));

        boolean matches = repository.matches(1L, "session-id", "refresh-token");

        assertThat(matches).isFalse();
    }

    @Test
    void matchesReturnsFalseWhenStoredHashIsMissing() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

        boolean matches = repository.matches(1L, "session-id", "refresh-token");

        assertThat(matches).isFalse();
    }

    @Test
    void deleteByMemberIdAndSessionIdDeletesRefreshTokenKey() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);

        repository.deleteByMemberIdAndSessionId(1L, "session-id");

        verify(stringRedisTemplate).delete("refresh:1:session-id");
    }

    @Test
    void saveRejectsBlankSessionId() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);

        assertThatThrownBy(() -> repository.save(1L, " ", "refresh-token", Duration.ofDays(14)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token session id is required.");
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
