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
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";
    private static final String SESSIONS_KEY_PREFIX = "refresh:sessions:";
    private static final RedisScript<Long> SAVE_SCRIPT = createScript("""
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SADD', KEYS[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            return 1
            """);
    private static final RedisScript<Long> DELETE_ALL_SCRIPT = createScript("""
            local refreshKeyPrefix = ARGV[1]
            local sessionIds = redis.call('SMEMBERS', KEYS[1])
            local keysToDelete = {}

            for _, sessionId in ipairs(sessionIds) do
                table.insert(keysToDelete, refreshKeyPrefix .. sessionId)
            end

            table.insert(keysToDelete, KEYS[1])
            return redis.call('DEL', unpack(keysToDelete))
            """);

    private final StringRedisTemplate stringRedisTemplate;

    public void save(Long memberId, String sessionId, String refreshToken, Duration ttl) {
        String sessionsKey = createSessionsKey(memberId);
        stringRedisTemplate.execute(
                SAVE_SCRIPT,
                List.of(createKey(memberId, sessionId), sessionsKey),
                TokenHashUtil.sha256(refreshToken),
                String.valueOf(ttl.toMillis()),
                sessionId
        );
    }

    public boolean matches(Long memberId, String sessionId, String refreshToken) {
        String savedRefreshTokenHash = stringRedisTemplate.opsForValue().get(createKey(memberId, sessionId));
        return TokenHashUtil.sha256(refreshToken).equals(savedRefreshTokenHash);
    }

    public boolean existsByMemberIdAndSessionId(Long memberId, String sessionId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(createKey(memberId, sessionId)));
    }

    public void deleteByMemberIdAndSessionId(Long memberId, String sessionId) {
        stringRedisTemplate.delete(createKey(memberId, sessionId));
        stringRedisTemplate.opsForSet().remove(createSessionsKey(memberId), sessionId);
    }

    public void deleteAllByMemberId(Long memberId) {
        String sessionsKey = createSessionsKey(memberId);
        stringRedisTemplate.execute(
                DELETE_ALL_SCRIPT,
                List.of(sessionsKey),
                KEY_PREFIX + memberId + ":"
        );
    }

    static String createKey(Long memberId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Refresh token session id is required.");
        }
        return KEY_PREFIX + memberId + ":" + sessionId;
    }

    private String createSessionsKey(Long memberId) {
        return SESSIONS_KEY_PREFIX + memberId;
    }

    private static RedisScript<Long> createScript(String scriptText) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(scriptText);
        redisScript.setResultType(Long.class);
        return redisScript;
    }
}
