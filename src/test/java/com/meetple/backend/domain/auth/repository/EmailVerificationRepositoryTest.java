package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.repository.EmailVerificationRepository.CodeVerificationResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class EmailVerificationRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void saveChallengeUsesHashedEmailKeyAndTtl() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        List<String> keys = List.of(
                "email-verification:challenge:" + emailHash,
                "email-verification:cooldown:" + emailHash
        );
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("code-hash"),
                eq("60000"),
                eq("300000")
        )).willReturn(1L);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        boolean saved = repository.saveChallengeIfAllowed(
                "user@meetple.com",
                "code-hash",
                Duration.ofMinutes(5),
                Duration.ofMinutes(1)
        );

        assertThat(saved).isTrue();
        assertThat(keys).allMatch(key -> !key.contains("user@meetple.com"));
    }

    @Test
    void verifyCodeMapsAtomicScriptResults() {
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<List<String>>any(),
                eq("code-hash"),
                eq("5")
        )).willReturn(1L, -1L, -2L, null);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        assertThat(repository.verifyCode("user@meetple.com", "code-hash", 5))
                .isEqualTo(CodeVerificationResult.VERIFIED);
        assertThat(repository.verifyCode("user@meetple.com", "code-hash", 5))
                .isEqualTo(CodeVerificationResult.INVALID);
        assertThat(repository.verifyCode("user@meetple.com", "code-hash", 5))
                .isEqualTo(CodeVerificationResult.ATTEMPTS_EXCEEDED);
        assertThat(repository.verifyCode("user@meetple.com", "code-hash", 5))
                .isEqualTo(CodeVerificationResult.EXPIRED);
    }

    @Test
    void saveSignupTokenStoresOnlyHashedTokenAndEmail() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        repository.saveSignupToken(
                "signup-token",
                "user@meetple.com",
                Duration.ofMinutes(15)
        );

        verify(valueOperations).set(
                "email-verification:signup-token:" + TokenHashUtil.sha256("signup-token"),
                TokenHashUtil.sha256("user@meetple.com"),
                Duration.ofMinutes(15)
        );
    }

    @Test
    void consumeSignupTokenExecutesAtomicCompareAndDelete() {
        String tokenKey = "email-verification:signup-token:"
                + TokenHashUtil.sha256("signup-token");
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(tokenKey)),
                eq(TokenHashUtil.sha256("user@meetple.com"))
        )).willReturn(1L);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        boolean consumed = repository.consumeSignupToken(
                "signup-token",
                "user@meetple.com"
        );

        assertThat(consumed).isTrue();
    }
}
