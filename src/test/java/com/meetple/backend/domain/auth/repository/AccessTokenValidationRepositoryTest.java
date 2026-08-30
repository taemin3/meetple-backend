package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AccessTokenValidationRepositoryTest {

    private static final List<String> VALIDATION_KEYS = List.of(
            "blacklist:access:3f16bed7089f4653e5ef21bfd2824d7f3aaaecc7a598e7e89c580e1606a9cc52",
            "refresh:1:session-id"
    );

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AccessTokenValidationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AccessTokenValidationRepository(stringRedisTemplate);
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    void getStatusReturnsActiveUsingOneMultiGetWhenSessionExists() {
        given(valueOperations.multiGet(VALIDATION_KEYS))
                .willReturn(Arrays.asList(null, "refresh-token-hash"));

        AccessTokenValidationRepository.Status status = repository.getStatus(
                "access-token",
                1L,
                "session-id"
        );

        assertThat(status).isEqualTo(AccessTokenValidationRepository.Status.ACTIVE);
        verify(valueOperations).multiGet(VALIDATION_KEYS);
        verify(stringRedisTemplate, never()).hasKey(VALIDATION_KEYS.get(0));
        verify(stringRedisTemplate, never()).hasKey(VALIDATION_KEYS.get(1));
    }

    @Test
    void getStatusPrioritizesBlacklistedWhenBothKeysExist() {
        given(valueOperations.multiGet(VALIDATION_KEYS))
                .willReturn(List.of("logout", "refresh-token-hash"));

        AccessTokenValidationRepository.Status status = repository.getStatus(
                "access-token",
                1L,
                "session-id"
        );

        assertThat(status).isEqualTo(AccessTokenValidationRepository.Status.BLACKLISTED);
    }

    @Test
    void getStatusReturnsInactiveWhenRefreshSessionIsMissing() {
        given(valueOperations.multiGet(VALIDATION_KEYS))
                .willReturn(Arrays.asList(null, null));

        AccessTokenValidationRepository.Status status = repository.getStatus(
                "access-token",
                1L,
                "session-id"
        );

        assertThat(status).isEqualTo(AccessTokenValidationRepository.Status.INACTIVE_SESSION);
    }

    @Test
    void getStatusRejectsUnexpectedRedisResponse() {
        given(valueOperations.multiGet(VALIDATION_KEYS)).willReturn(null);

        assertThatThrownBy(() -> repository.getStatus("access-token", 1L, "session-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Access token validation returned an unexpected Redis response.");
    }
}
