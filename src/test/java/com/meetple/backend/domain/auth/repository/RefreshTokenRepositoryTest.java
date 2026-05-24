package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Test
    void saveStoresHashedRefreshTokenWithTtl() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);

        repository.save(1L, "session-id", "refresh-token", Duration.ofDays(14));

        String hashedRefreshToken = sha256("refresh-token");
        verify(stringRedisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("refresh:1:session-id", "refresh:sessions:1")),
                eq(hashedRefreshToken),
                eq(String.valueOf(Duration.ofDays(14).toMillis())),
                eq("session-id")
        );
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
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);

        repository.deleteByMemberIdAndSessionId(1L, "session-id");

        verify(stringRedisTemplate).delete("refresh:1:session-id");
        verify(setOperations).remove("refresh:sessions:1", "session-id");
    }

    @Test
    void existsByMemberIdAndSessionIdReturnsTrueWhenRefreshTokenKeyExists() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.hasKey("refresh:1:session-id")).willReturn(true);

        boolean exists = repository.existsByMemberIdAndSessionId(1L, "session-id");

        assertThat(exists).isTrue();
    }

    @Test
    void deleteAllByMemberIdExecutesAtomicDeleteScript() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);

        repository.deleteAllByMemberId(1L);

        verify(stringRedisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of("refresh:sessions:1")),
                eq("refresh:1:")
        );
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
