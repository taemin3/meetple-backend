package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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
class AccountDeletionRepositoryTest {

    @Mock
    private StringRedisTemplate redis;

    @Test
    void verificationUsesPurposeSpecificHashedKeys() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        String tokenHash = TokenHashUtil.sha256("deletion-token");
        List<String> keys = List.of(
                "account-deletion:challenge:" + emailHash,
                "account-deletion:token:" + tokenHash,
                "account-deletion:email-token:" + emailHash
        );
        given(redis.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("code-hash"),
                eq("5"),
                eq(emailHash),
                eq("900000"),
                eq(tokenHash),
                eq("account-deletion:token:"),
                eq(TokenHashUtil.sha256("127.0.0.1"))
        )).willReturn(1L);
        AccountDeletionRepository repository = new AccountDeletionRepository(redis);

        assertThat(repository.verifyCodeAndSaveToken(
                "user@meetple.com",
                "code-hash",
                5,
                "deletion-token",
                Duration.ofMinutes(15),
                "127.0.0.1"
        )).isEqualTo(AccountDeletionRepository.CodeVerificationResult.VERIFIED);
        assertThat(keys).allMatch(key -> !key.contains("user@meetple.com"));
    }

    @Test
    void claimConsumesOnlyMatchingOneTimeToken() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        String tokenHash = TokenHashUtil.sha256("deletion-token");
        List<String> keys = List.of(
                "account-deletion:token:" + tokenHash,
                "account-deletion:email-token:" + emailHash
        );
        given(redis.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq(emailHash),
                eq(tokenHash)
        )).willReturn(1L, 0L);
        AccountDeletionRepository repository = new AccountDeletionRepository(redis);

        assertThat(repository.claimToken("deletion-token", "user@meetple.com")).isTrue();
        assertThat(repository.claimToken("deletion-token", "user@meetple.com")).isFalse();
    }
}
