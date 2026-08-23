package com.meetple.backend.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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
    void verifyCodeAndSaveSignupTokenMapsAtomicScriptResults() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        String tokenHash = TokenHashUtil.sha256("signup-token");
        List<String> keys = List.of(
                "email-verification:challenge:" + emailHash,
                "email-verification:signup-token:" + tokenHash
        );
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("code-hash"),
                eq("5"),
                eq(emailHash),
                eq("900000")
        )).willReturn(1L, -1L, -2L, null);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        assertThat(repository.verifyCodeAndSaveSignupToken(
                "user@meetple.com",
                "code-hash",
                5,
                "signup-token",
                Duration.ofMinutes(15)
        ))
                .isEqualTo(CodeVerificationResult.VERIFIED);
        assertThat(repository.verifyCodeAndSaveSignupToken(
                "user@meetple.com",
                "code-hash",
                5,
                "signup-token",
                Duration.ofMinutes(15)
        ))
                .isEqualTo(CodeVerificationResult.INVALID);
        assertThat(repository.verifyCodeAndSaveSignupToken(
                "user@meetple.com",
                "code-hash",
                5,
                "signup-token",
                Duration.ofMinutes(15)
        ))
                .isEqualTo(CodeVerificationResult.ATTEMPTS_EXCEEDED);
        assertThat(repository.verifyCodeAndSaveSignupToken(
                "user@meetple.com",
                "code-hash",
                5,
                "signup-token",
                Duration.ofMinutes(15)
        ))
                .isEqualTo(CodeVerificationResult.EXPIRED);
        assertThat(keys).allMatch(key -> !key.contains("user@meetple.com"));
    }

    @Test
    void deleteChallengeOnlyWhenCodeHashMatches() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        List<String> keys = List.of(
                "email-verification:challenge:" + emailHash,
                "email-verification:cooldown:" + emailHash
        );
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("code-hash")
        )).willReturn(1L);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        assertThat(repository.deleteChallengeIfMatches("user@meetple.com", "code-hash"))
                .isTrue();
    }

    @Test
    void findsRemainingTtlOnlyForCurrentChallengeHash() {
        String emailHash = TokenHashUtil.sha256("user@meetple.com");
        List<String> keys = List.of("email-verification:challenge:" + emailHash);
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("code-hash")
        )).willReturn(180000L, 0L);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        assertThat(repository.findChallengeRemainingTtlIfMatches(
                "user@meetple.com",
                "code-hash"
        )).isEqualTo(Duration.ofMinutes(3));
        assertThat(repository.findChallengeRemainingTtlIfMatches(
                "user@meetple.com",
                "code-hash"
        )).isZero();
    }

    @Test
    void acquireSendPermitUsesHashedRequesterAndGlobalKeys() {
        String requesterHash = TokenHashUtil.sha256("127.0.0.1");
        List<String> keys = List.of(
                "email-verification:rate-limit:requester:" + requesterHash,
                "email-verification:rate-limit:global"
        );
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("5"),
                eq("60000"),
                eq("100"),
                eq("60000")
        )).willReturn(1L);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        boolean allowed = repository.acquireSendPermit(
                "127.0.0.1",
                Duration.ofMinutes(1),
                5,
                Duration.ofMinutes(1),
                100
        );

        assertThat(allowed).isTrue();
        assertThat(keys.getFirst()).doesNotContain("127.0.0.1");
    }

    @Test
    void acquireConfirmPermitUsesHashedRequesterKey() {
        String requesterHash = TokenHashUtil.sha256("127.0.0.1");
        List<String> keys = List.of(
                "email-verification:rate-limit:confirm-requester:" + requesterHash
        );
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(keys),
                eq("10"),
                eq("60000")
        )).willReturn(1L);
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        boolean allowed = repository.acquireConfirmPermit(
                "127.0.0.1",
                Duration.ofMinutes(1),
                10
        );

        assertThat(allowed).isTrue();
        assertThat(keys.getFirst()).doesNotContain("127.0.0.1");
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

    @Test
    void matchesSignupTokenComparesHashedTokenAndEmail() {
        String tokenKey = "email-verification:signup-token:"
                + TokenHashUtil.sha256("signup-token");
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(tokenKey))
                .willReturn(TokenHashUtil.sha256("user@meetple.com"));
        EmailVerificationRepository repository = new EmailVerificationRepository(
                stringRedisTemplate
        );

        assertThat(repository.matchesSignupToken("signup-token", "user@meetple.com"))
                .isTrue();
    }
}
