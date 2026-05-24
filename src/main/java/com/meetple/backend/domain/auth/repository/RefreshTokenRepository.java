package com.meetple.backend.domain.auth.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";
    private static final String SESSIONS_KEY_PREFIX = "refresh:sessions:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(Long memberId, String sessionId, String refreshToken, Duration ttl) {
        stringRedisTemplate.opsForValue().set(createKey(memberId, sessionId), TokenHashUtil.sha256(refreshToken), ttl);
        stringRedisTemplate.opsForSet().add(createSessionsKey(memberId), sessionId);
        stringRedisTemplate.expire(createSessionsKey(memberId), ttl);
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
        Set<String> sessionIds = stringRedisTemplate.opsForSet().members(sessionsKey);

        if (sessionIds == null || sessionIds.isEmpty()) {
            stringRedisTemplate.delete(sessionsKey);
            return;
        }

        ArrayList<String> keys = new ArrayList<>();
        sessionIds.stream()
                .map(sessionId -> createKey(memberId, sessionId))
                .forEach(keys::add);
        keys.add(sessionsKey);

        stringRedisTemplate.delete(keys);
    }

    private String createKey(Long memberId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Refresh token session id is required.");
        }
        return KEY_PREFIX + memberId + ":" + sessionId;
    }

    private String createSessionsKey(Long memberId) {
        return SESSIONS_KEY_PREFIX + memberId;
    }
}
