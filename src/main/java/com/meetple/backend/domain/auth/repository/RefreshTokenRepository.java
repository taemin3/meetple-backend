package com.meetple.backend.domain.auth.repository;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(Long memberId, String sessionId, String refreshToken, Duration ttl) {
        stringRedisTemplate.opsForValue().set(createKey(memberId, sessionId), TokenHashUtil.sha256(refreshToken), ttl);
    }

    public boolean matches(Long memberId, String sessionId, String refreshToken) {
        String savedRefreshTokenHash = stringRedisTemplate.opsForValue().get(createKey(memberId, sessionId));
        return TokenHashUtil.sha256(refreshToken).equals(savedRefreshTokenHash);
    }

    public void deleteByMemberIdAndSessionId(Long memberId, String sessionId) {
        stringRedisTemplate.delete(createKey(memberId, sessionId));
    }

    private String createKey(Long memberId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Refresh token session id is required.");
        }
        return KEY_PREFIX + memberId + ":" + sessionId;
    }
}
