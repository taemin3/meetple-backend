package com.meetple.backend.domain.auth.repository;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PasswordResetRepository {

    private static final String CHALLENGE_KEY_PREFIX = "password-reset:challenge:";
    private static final String COOLDOWN_KEY_PREFIX = "password-reset:cooldown:";
    private static final String TOKEN_KEY_PREFIX = "password-reset:token:";
    private static final String EMAIL_TOKEN_KEY_PREFIX = "password-reset:email-token:";
    private static final String REQUESTER_RATE_LIMIT_KEY_PREFIX = "password-reset:rate-limit:requester:";
    private static final String CONFIRM_REQUESTER_RATE_LIMIT_KEY_PREFIX =
            "password-reset:rate-limit:confirm-requester:";
    private static final String GLOBAL_RATE_LIMIT_KEY = "password-reset:rate-limit:global";

    private static final RedisScript<Long> SAVE_CHALLENGE_SCRIPT = createScript("""
            local cooldownCreated = redis.call('SET', KEYS[2], '1', 'PX', ARGV[2], 'NX')
            if not cooldownCreated then
                return 0
            end

            redis.call('HSET', KEYS[1], 'codeHash', ARGV[1], 'attempts', '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """);
    private static final RedisScript<Long> VERIFY_CODE_SCRIPT = createScript("""
            local savedCodeHash = redis.call('HGET', KEYS[1], 'codeHash')
            if not savedCodeHash then
                return 0
            end

            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
            local maxAttempts = tonumber(ARGV[2])
            if attempts >= maxAttempts then
                redis.call('DEL', KEYS[1])
                return -2
            end

            if savedCodeHash ~= ARGV[1] then
                attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
                if attempts >= maxAttempts then
                    redis.call('DEL', KEYS[1])
                    return -2
                end
                return -1
            end

            local previousTokenHash = redis.call('GET', KEYS[3])
            if previousTokenHash then
                redis.call('DEL', ARGV[6] .. previousTokenHash)
            end
            redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])
            redis.call('SET', KEYS[3], ARGV[5], 'PX', ARGV[4])
            redis.call('DEL', KEYS[1])
            return 1
            """);
    private static final RedisScript<Long> DELETE_CHALLENGE_IF_MATCHES_SCRIPT = createScript("""
            local savedCodeHash = redis.call('HGET', KEYS[1], 'codeHash')
            if not savedCodeHash or savedCodeHash ~= ARGV[1] then
                return 0
            end

            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """);
    private static final RedisScript<Long> ACQUIRE_CONFIRM_PERMIT_SCRIPT = createScript("""
            local requesterCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            if requesterCount >= tonumber(ARGV[1]) then
                return 0
            end

            requesterCount = redis.call('INCR', KEYS[1])
            if requesterCount == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 1
            """);
    private static final RedisScript<Long> ACQUIRE_SEND_PERMIT_SCRIPT = createScript("""
            local requesterCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            local globalCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if requesterCount >= tonumber(ARGV[1]) or globalCount >= tonumber(ARGV[3]) then
                return 0
            end

            requesterCount = redis.call('INCR', KEYS[1])
            if requesterCount == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end

            globalCount = redis.call('INCR', KEYS[2])
            if globalCount == 1 then
                redis.call('PEXPIRE', KEYS[2], ARGV[4])
            end
            return 1
            """);
    private static final RedisScript<Long> CLAIM_TOKEN_SCRIPT = createScript("""
            local savedEmailHash = redis.call('GET', KEYS[1])
            local currentTokenHash = redis.call('GET', KEYS[2])
            if not savedEmailHash or savedEmailHash ~= ARGV[1]
                    or not currentTokenHash or currentTokenHash ~= ARGV[2] then
                return 0
            end

            local remainingTtl = redis.call('PTTL', KEYS[1])
            if remainingTtl <= 0 then
                return 0
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return remainingTtl
            """);
    private static final RedisScript<Long> RESTORE_TOKEN_SCRIPT = createScript("""
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            return 1
            """);

    private final StringRedisTemplate stringRedisTemplate;

    public boolean saveChallengeIfAllowed(
            String email,
            String codeHash,
            Duration codeTtl,
            Duration resendCooldown
    ) {
        String emailHash = TokenHashUtil.sha256(email);
        Long result = stringRedisTemplate.execute(
                SAVE_CHALLENGE_SCRIPT,
                List.of(createChallengeKey(emailHash), createCooldownKey(emailHash)),
                codeHash,
                String.valueOf(resendCooldown.toMillis()),
                String.valueOf(codeTtl.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }

    public CodeVerificationResult verifyCodeAndSaveResetToken(
            String email,
            String codeHash,
            int maxAttempts,
            String resetToken,
            Duration resetTokenTtl
    ) {
        String emailHash = TokenHashUtil.sha256(email);
        String tokenHash = TokenHashUtil.sha256(resetToken);
        Long result = stringRedisTemplate.execute(
                VERIFY_CODE_SCRIPT,
                List.of(
                        createChallengeKey(emailHash),
                        createTokenKey(tokenHash),
                        createEmailTokenKey(emailHash)
                ),
                codeHash,
                String.valueOf(maxAttempts),
                emailHash,
                String.valueOf(resetTokenTtl.toMillis()),
                tokenHash,
                TOKEN_KEY_PREFIX
        );
        return CodeVerificationResult.from(result);
    }

    public boolean deleteChallengeIfMatches(String email, String codeHash) {
        String emailHash = TokenHashUtil.sha256(email);
        Long result = stringRedisTemplate.execute(
                DELETE_CHALLENGE_IF_MATCHES_SCRIPT,
                List.of(createChallengeKey(emailHash), createCooldownKey(emailHash)),
                codeHash
        );
        return Long.valueOf(1L).equals(result);
    }

    public boolean acquireSendPermit(
            String requesterIdentifier,
            Duration requesterWindow,
            int requesterLimit,
            Duration globalWindow,
            int globalLimit
    ) {
        Long result = stringRedisTemplate.execute(
                ACQUIRE_SEND_PERMIT_SCRIPT,
                List.of(
                        REQUESTER_RATE_LIMIT_KEY_PREFIX
                                + TokenHashUtil.sha256(requesterIdentifier),
                        GLOBAL_RATE_LIMIT_KEY
                ),
                String.valueOf(requesterLimit),
                String.valueOf(requesterWindow.toMillis()),
                String.valueOf(globalLimit),
                String.valueOf(globalWindow.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }

    public boolean acquireConfirmPermit(
            String requesterIdentifier,
            Duration requesterWindow,
            int requesterLimit
    ) {
        Long result = stringRedisTemplate.execute(
                ACQUIRE_CONFIRM_PERMIT_SCRIPT,
                List.of(CONFIRM_REQUESTER_RATE_LIMIT_KEY_PREFIX
                        + TokenHashUtil.sha256(requesterIdentifier)),
                String.valueOf(requesterLimit),
                String.valueOf(requesterWindow.toMillis())
        );
        return Long.valueOf(1L).equals(result);
    }

    public Duration claimResetToken(String token, String email) {
        String emailHash = TokenHashUtil.sha256(email);
        String tokenHash = TokenHashUtil.sha256(token);
        Long remainingTtlMillis = stringRedisTemplate.execute(
                CLAIM_TOKEN_SCRIPT,
                List.of(createTokenKey(tokenHash), createEmailTokenKey(emailHash)),
                emailHash,
                tokenHash
        );
        if (remainingTtlMillis == null || remainingTtlMillis <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(remainingTtlMillis);
    }

    public void restoreResetTokenIfNoNewerToken(
            String token,
            String email,
            Duration remainingTtl
    ) {
        if (remainingTtl.isZero() || remainingTtl.isNegative()) {
            return;
        }
        String emailHash = TokenHashUtil.sha256(email);
        String tokenHash = TokenHashUtil.sha256(token);
        stringRedisTemplate.execute(
                RESTORE_TOKEN_SCRIPT,
                List.of(createTokenKey(tokenHash), createEmailTokenKey(emailHash)),
                emailHash,
                tokenHash,
                String.valueOf(remainingTtl.toMillis())
        );
    }

    private String createChallengeKey(String emailHash) {
        return CHALLENGE_KEY_PREFIX + emailHash;
    }

    private String createCooldownKey(String emailHash) {
        return COOLDOWN_KEY_PREFIX + emailHash;
    }

    private String createTokenKey(String tokenHash) {
        return TOKEN_KEY_PREFIX + tokenHash;
    }

    private String createEmailTokenKey(String emailHash) {
        return EMAIL_TOKEN_KEY_PREFIX + emailHash;
    }

    private static RedisScript<Long> createScript(String scriptText) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(scriptText);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    public enum CodeVerificationResult {
        VERIFIED,
        INVALID,
        EXPIRED,
        ATTEMPTS_EXCEEDED;

        private static CodeVerificationResult from(Long result) {
            if (Long.valueOf(1L).equals(result)) {
                return VERIFIED;
            }
            if (Long.valueOf(-1L).equals(result)) {
                return INVALID;
            }
            if (Long.valueOf(-2L).equals(result)) {
                return ATTEMPTS_EXCEEDED;
            }
            return EXPIRED;
        }
    }
}
