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
public class AccountDeletionRepository {

    private static final String CHALLENGE_PREFIX = "account-deletion:challenge:";
    private static final String COOLDOWN_PREFIX = "account-deletion:cooldown:";
    private static final String TOKEN_PREFIX = "account-deletion:token:";
    private static final String EMAIL_TOKEN_PREFIX = "account-deletion:email-token:";
    private static final String REQUESTER_RATE_PREFIX = "account-deletion:rate-limit:requester:";
    private static final String CONFIRM_RATE_PREFIX = "account-deletion:rate-limit:confirm-requester:";
    private static final String GLOBAL_RATE_KEY = "account-deletion:rate-limit:global";

    private static final RedisScript<Long> SAVE_CHALLENGE = script("""
            local created = redis.call('SET', KEYS[2], '1', 'PX', ARGV[2], 'NX')
            if not created then return 0 end
            redis.call('HSET', KEYS[1], 'codeHash', ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """);
    private static final RedisScript<Long> VERIFY_CODE = script("""
            local saved = redis.call('HGET', KEYS[1], 'codeHash')
            if not saved then return 0 end
            local field = 'attempts:' .. ARGV[7]
            local attempts = tonumber(redis.call('HGET', KEYS[1], field) or '0')
            if attempts >= tonumber(ARGV[2]) then return -2 end
            if saved ~= ARGV[1] then
                attempts = redis.call('HINCRBY', KEYS[1], field, 1)
                if attempts >= tonumber(ARGV[2]) then return -2 end
                return -1
            end
            local previous = redis.call('GET', KEYS[3])
            if previous then redis.call('DEL', ARGV[6] .. previous) end
            redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])
            redis.call('SET', KEYS[3], ARGV[5], 'PX', ARGV[4])
            redis.call('DEL', KEYS[1])
            return 1
            """);
    private static final RedisScript<Long> CLAIM_TOKEN = script("""
            local emailHash = redis.call('GET', KEYS[1])
            local currentToken = redis.call('GET', KEYS[2])
            if not emailHash or emailHash ~= ARGV[1]
                    or not currentToken or currentToken ~= ARGV[2] then return 0 end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """);
    private static final RedisScript<Long> DELETE_CHALLENGE = script("""
            local saved = redis.call('HGET', KEYS[1], 'codeHash')
            if not saved or saved ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """);
    private static final RedisScript<Long> FIND_TTL = script("""
            local saved = redis.call('HGET', KEYS[1], 'codeHash')
            if not saved or saved ~= ARGV[1] then return 0 end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then return 0 end
            return ttl
            """);
    private static final RedisScript<Long> RATE_LIMIT = script("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then return 0 end
            count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            return 1
            """);
    private static final RedisScript<Long> SEND_RATE_LIMIT = script("""
            local requester = tonumber(redis.call('GET', KEYS[1]) or '0')
            local global = tonumber(redis.call('GET', KEYS[2]) or '0')
            if requester >= tonumber(ARGV[1]) or global >= tonumber(ARGV[3]) then return 0 end
            requester = redis.call('INCR', KEYS[1])
            if requester == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            global = redis.call('INCR', KEYS[2])
            if global == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[4]) end
            return 1
            """);

    private final StringRedisTemplate redis;

    public boolean saveChallengeIfAllowed(String email, String codeHash, Duration ttl, Duration cooldown) {
        String emailHash = TokenHashUtil.sha256(email);
        return Long.valueOf(1).equals(redis.execute(SAVE_CHALLENGE,
                List.of(CHALLENGE_PREFIX + emailHash, COOLDOWN_PREFIX + emailHash),
                codeHash, String.valueOf(cooldown.toMillis()), String.valueOf(ttl.toMillis())));
    }

    public CodeVerificationResult verifyCodeAndSaveToken(
            String email, String codeHash, int maxAttempts, String token,
            Duration tokenTtl, String requesterIdentifier
    ) {
        String emailHash = TokenHashUtil.sha256(email);
        String tokenHash = TokenHashUtil.sha256(token);
        Long result = redis.execute(VERIFY_CODE, List.of(
                        CHALLENGE_PREFIX + emailHash,
                        TOKEN_PREFIX + tokenHash,
                        EMAIL_TOKEN_PREFIX + emailHash),
                codeHash, String.valueOf(maxAttempts), emailHash,
                String.valueOf(tokenTtl.toMillis()), tokenHash, TOKEN_PREFIX,
                TokenHashUtil.sha256(requesterIdentifier));
        return CodeVerificationResult.from(result);
    }

    public boolean claimToken(String token, String email) {
        String emailHash = TokenHashUtil.sha256(email);
        String tokenHash = TokenHashUtil.sha256(token);
        return Long.valueOf(1).equals(redis.execute(CLAIM_TOKEN,
                List.of(TOKEN_PREFIX + tokenHash, EMAIL_TOKEN_PREFIX + emailHash),
                emailHash, tokenHash));
    }

    public boolean deleteChallengeIfMatches(String email, String codeHash) {
        String hash = TokenHashUtil.sha256(email);
        return Long.valueOf(1).equals(redis.execute(DELETE_CHALLENGE,
                List.of(CHALLENGE_PREFIX + hash, COOLDOWN_PREFIX + hash), codeHash));
    }

    public Duration findChallengeRemainingTtlIfMatches(String email, String codeHash) {
        Long millis = redis.execute(FIND_TTL,
                List.of(CHALLENGE_PREFIX + TokenHashUtil.sha256(email)), codeHash);
        return millis == null || millis <= 0 ? Duration.ZERO : Duration.ofMillis(millis);
    }

    public boolean acquireSendPermit(String requester, Duration requesterWindow, int requesterLimit,
                                     Duration globalWindow, int globalLimit) {
        return Long.valueOf(1).equals(redis.execute(SEND_RATE_LIMIT,
                List.of(REQUESTER_RATE_PREFIX + TokenHashUtil.sha256(requester), GLOBAL_RATE_KEY),
                String.valueOf(requesterLimit), String.valueOf(requesterWindow.toMillis()),
                String.valueOf(globalLimit), String.valueOf(globalWindow.toMillis())));
    }

    public boolean acquireConfirmPermit(String requester, Duration window, int limit) {
        return Long.valueOf(1).equals(redis.execute(RATE_LIMIT,
                List.of(CONFIRM_RATE_PREFIX + TokenHashUtil.sha256(requester)),
                String.valueOf(limit), String.valueOf(window.toMillis())));
    }

    private static RedisScript<Long> script(String text) {
        DefaultRedisScript<Long> value = new DefaultRedisScript<>();
        value.setScriptText(text);
        value.setResultType(Long.class);
        return value;
    }

    public enum CodeVerificationResult {
        VERIFIED, INVALID, EXPIRED, ATTEMPTS_EXCEEDED;

        static CodeVerificationResult from(Long result) {
            if (Long.valueOf(1).equals(result)) return VERIFIED;
            if (Long.valueOf(-1).equals(result)) return INVALID;
            if (Long.valueOf(-2).equals(result)) return ATTEMPTS_EXCEEDED;
            return EXPIRED;
        }
    }
}
