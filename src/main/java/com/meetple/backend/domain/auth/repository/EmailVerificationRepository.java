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
public class EmailVerificationRepository {

    private static final String CHALLENGE_KEY_PREFIX = "email-verification:challenge:";
    private static final String COOLDOWN_KEY_PREFIX = "email-verification:cooldown:";
    private static final String SIGNUP_TOKEN_KEY_PREFIX = "email-verification:signup-token:";
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

            redis.call('DEL', KEYS[1])
            return 1
            """);
    private static final RedisScript<Long> CONSUME_SIGNUP_TOKEN_SCRIPT = createScript("""
            local savedEmailHash = redis.call('GET', KEYS[1])
            if not savedEmailHash or savedEmailHash ~= ARGV[1] then
                return 0
            end

            redis.call('DEL', KEYS[1])
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

    public CodeVerificationResult verifyCode(String email, String codeHash, int maxAttempts) {
        String emailHash = TokenHashUtil.sha256(email);
        Long result = stringRedisTemplate.execute(
                VERIFY_CODE_SCRIPT,
                List.of(createChallengeKey(emailHash)),
                codeHash,
                String.valueOf(maxAttempts)
        );
        return CodeVerificationResult.from(result);
    }

    public void deleteChallenge(String email) {
        String emailHash = TokenHashUtil.sha256(email);
        stringRedisTemplate.delete(List.of(
                createChallengeKey(emailHash),
                createCooldownKey(emailHash)
        ));
    }

    public void saveSignupToken(String token, String email, Duration ttl) {
        stringRedisTemplate.opsForValue().set(
                createSignupTokenKey(TokenHashUtil.sha256(token)),
                TokenHashUtil.sha256(email),
                ttl
        );
    }

    public boolean consumeSignupToken(String token, String email) {
        Long result = stringRedisTemplate.execute(
                CONSUME_SIGNUP_TOKEN_SCRIPT,
                List.of(createSignupTokenKey(TokenHashUtil.sha256(token))),
                TokenHashUtil.sha256(email)
        );
        return Long.valueOf(1L).equals(result);
    }

    private String createChallengeKey(String emailHash) {
        return CHALLENGE_KEY_PREFIX + emailHash;
    }

    private String createCooldownKey(String emailHash) {
        return COOLDOWN_KEY_PREFIX + emailHash;
    }

    private String createSignupTokenKey(String tokenHash) {
        return SIGNUP_TOKEN_KEY_PREFIX + tokenHash;
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
