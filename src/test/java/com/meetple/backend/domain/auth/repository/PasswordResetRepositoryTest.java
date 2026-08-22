package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.repository.PasswordResetRepository.CodeVerificationResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class PasswordResetRepositoryTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void saveChallengeUsesPasswordResetNamespaceAndHashedEmail() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        List<String> keys = List.of(
                "password-reset:challenge:" + emailHash,
                "password-reset:cooldown:" + emailHash
        );
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("code-hash"),
                eq("60000"),
                eq("300000")
        )).willReturn(1L);
        PasswordResetRepository repository = new PasswordResetRepository(stringRedisTemplate);

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
    void verifyCodeStoresOnlyLatestResetTokenForHashedEmail() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        String tokenHash = TokenHashUtil.sha256("reset-token");
        List<String> keys = List.of(
                "password-reset:challenge:" + emailHash,
                "password-reset:token:" + tokenHash,
                "password-reset:email-token:" + emailHash
        );
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("code-hash"),
                eq("5"),
                eq(emailHash),
                eq("900000"),
                eq(tokenHash),
                eq("password-reset:token:")
        )).willReturn(1L, -1L, -2L, null);
        PasswordResetRepository repository = new PasswordResetRepository(stringRedisTemplate);

        assertThat(repository.verifyCodeAndSaveResetToken(
                "user@meetple.com", "code-hash", 5, "reset-token", Duration.ofMinutes(15)
        )).isEqualTo(CodeVerificationResult.VERIFIED);
        assertThat(repository.verifyCodeAndSaveResetToken(
                "user@meetple.com", "code-hash", 5, "reset-token", Duration.ofMinutes(15)
        )).isEqualTo(CodeVerificationResult.INVALID);
        assertThat(repository.verifyCodeAndSaveResetToken(
                "user@meetple.com", "code-hash", 5, "reset-token", Duration.ofMinutes(15)
        )).isEqualTo(CodeVerificationResult.ATTEMPTS_EXCEEDED);
        assertThat(repository.verifyCodeAndSaveResetToken(
                "user@meetple.com", "code-hash", 5, "reset-token", Duration.ofMinutes(15)
        )).isEqualTo(CodeVerificationResult.EXPIRED);
    }

    @Test
    void claimResetTokenReturnsRemainingTtlAndUsesHashedKeys() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        String tokenHash = TokenHashUtil.sha256("reset-token");
        List<String> keys = List.of(
                "password-reset:token:" + tokenHash,
                "password-reset:email-token:" + emailHash
        );
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq(emailHash),
                eq(tokenHash)
        )).willReturn(120_000L);
        PasswordResetRepository repository = new PasswordResetRepository(stringRedisTemplate);

        Duration remainingTtl = repository.claimResetToken("reset-token", "user@meetple.com");

        assertThat(remainingTtl).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void restoreResetTokenDoesNotExposeRawEmailOrTokenInKeys() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        String tokenHash = TokenHashUtil.sha256("reset-token");
        List<String> keys = List.of(
                "password-reset:token:" + tokenHash,
                "password-reset:email-token:" + emailHash
        );
        PasswordResetRepository repository = new PasswordResetRepository(stringRedisTemplate);

        repository.restoreResetTokenIfNoNewerToken(
                "reset-token",
                "user@meetple.com",
                Duration.ofMinutes(2)
        );

        verify(stringRedisTemplate).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq(emailHash),
                eq(tokenHash),
                eq("120000")
        );
        assertThat(keys).allMatch(key -> !key.contains("user@meetple.com"));
    }
}
