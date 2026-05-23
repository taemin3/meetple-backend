package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AccessTokenBlacklistRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void saveStoresHashedAccessTokenWithTtl() {
        AccessTokenBlacklistRepository repository = new AccessTokenBlacklistRepository(stringRedisTemplate);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);

        repository.save("access-token", Duration.ofMinutes(10));

        verify(valueOperations).set(keyCaptor.capture(), eq("logout"), eq(Duration.ofMinutes(10)));
        assertThat(keyCaptor.getValue()).startsWith("blacklist:access:");
        assertThat(keyCaptor.getValue()).doesNotContain("access-token");
    }

    @Test
    void saveSkipsNonPositiveTtl() {
        AccessTokenBlacklistRepository repository = new AccessTokenBlacklistRepository(stringRedisTemplate);

        repository.save("access-token", Duration.ZERO);

        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void existsReturnsTrueWhenBlacklistKeyExists() {
        AccessTokenBlacklistRepository repository = new AccessTokenBlacklistRepository(stringRedisTemplate);
        given(stringRedisTemplate.hasKey("blacklist:access:3f16bed7089f4653e5ef21bfd2824d7f3aaaecc7a598e7e89c580e1606a9cc52"))
                .willReturn(true);

        assertThat(repository.exists("access-token")).isTrue();
    }
}
