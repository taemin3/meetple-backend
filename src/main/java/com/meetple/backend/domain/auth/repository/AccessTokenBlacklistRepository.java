package com.meetple.backend.domain.auth.repository;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccessTokenBlacklistRepository {

    private static final String KEY_PREFIX = "blacklist:access:";
    private static final String BLACKLIST_VALUE = "logout";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String accessToken, Duration ttl) {
        if (ttl.isPositive()) {
            stringRedisTemplate.opsForValue().set(createKey(accessToken), BLACKLIST_VALUE, ttl);
        }
    }

    public boolean exists(String accessToken) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(createKey(accessToken)));
    }

    static String createKey(String accessToken) {
        return KEY_PREFIX + TokenHashUtil.sha256(accessToken);
    }
}
