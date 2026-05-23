package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;
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
    void saveStoresRefreshTokenWithTtl() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

        repository.save(1L, "refresh-token", Duration.ofDays(14));

        verify(valueOperations).set("refresh:1", "refresh-token", Duration.ofDays(14));
    }

    @Test
    void findByMemberIdReturnsRefreshToken() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:1")).willReturn("refresh-token");

        Optional<String> refreshToken = repository.findByMemberId(1L);

        assertThat(refreshToken).contains("refresh-token");
    }

    @Test
    void deleteByMemberIdDeletesRefreshTokenKey() {
        RefreshTokenRepository repository = new RefreshTokenRepository(stringRedisTemplate);

        repository.deleteByMemberId(1L);

        verify(stringRedisTemplate).delete("refresh:1");
    }
}
