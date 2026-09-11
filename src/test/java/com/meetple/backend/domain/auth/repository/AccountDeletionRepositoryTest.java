package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
    void saveChallengeBindsMemberAndReplacesPreviousAttempts() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        List<String> keys = List.of(
                "account-deletion:challenge:" + emailHash,
                "account-deletion:cooldown:" + emailHash
        );
        given(redis.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("code-hash"),
                eq("60000"),
                eq("7"),
                eq("300000")
        )).willReturn(1L);
        AccountDeletionRepository repository = new AccountDeletionRepository(redis);

        assertThat(repository.saveChallengeIfAllowed(
                "user@meetple.com",
                "code-hash",
                7L,
                Duration.ofMinutes(5),
                Duration.ofMinutes(1)
        )).isTrue();
    }

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
                ArgumentMatchers.<RedisScript<String>>any(),
                eq(keys),
                eq(emailHash),
                eq(tokenHash)
        )).willReturn("7|120000", (String) null);
        AccountDeletionRepository repository = new AccountDeletionRepository(redis);

        assertThat(repository.claimToken("deletion-token", "user@meetple.com"))
                .contains(new AccountDeletionRepository.ClaimedToken(
                        7L,
                        Duration.ofMinutes(2)
                ));
        assertThat(repository.claimToken("deletion-token", "user@meetple.com")).isEmpty();
    }

    @Test
    void restoreTokenPreservesMemberBindingWithoutRawIdentifiersInKeys() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        String tokenHash = TokenHashUtil.sha256("deletion-token");
        List<String> keys = List.of(
                "account-deletion:token:" + tokenHash,
                "account-deletion:email-token:" + emailHash
        );
        AccountDeletionRepository repository = new AccountDeletionRepository(redis);

        repository.restoreTokenIfNoNewerToken(
                "deletion-token",
                "user@meetple.com",
                7L,
                Duration.ofMinutes(2)
        );

        verify(redis).execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq(emailHash),
                eq("7"),
                eq(tokenHash),
                eq("120000")
        );
    }
}
