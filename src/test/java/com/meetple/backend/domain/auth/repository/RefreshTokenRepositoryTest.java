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
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

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
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);

        repository.save(1L, "session-id", "refresh-token", Duration.ofDays(14));

        String hashedRefreshToken = sha256("refresh-token");
        verify(valueOperations).set("refresh:1:session-id", hashedRefreshToken, Duration.ofDays(14));
        verify(setOperations).add("refresh:sessions:1", "session-id");
        verify(stringRedisTemplate).expire("refresh:sessions:1", Duration.ofDays(14));
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
    void deleteAllByMemberIdDeletesAllRefreshTokenKeysAndSessionSet() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.members("refresh:sessions:1"))
                .willReturn(new LinkedHashSet<>(List.of("session-1", "session-2")));

        repository.deleteAllByMemberId(1L);

        verify(stringRedisTemplate).delete(List.of(
                "refresh:1:session-1",
                "refresh:1:session-2",
                "refresh:sessions:1"
        ));
    }

    @Test
    void deleteAllByMemberIdDeletesSessionSetWhenSessionIdsAreMissing() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForSet()).willReturn(setOperations);

        repository.deleteAllByMemberId(1L);

        verify(stringRedisTemplate).delete("refresh:sessions:1");
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
