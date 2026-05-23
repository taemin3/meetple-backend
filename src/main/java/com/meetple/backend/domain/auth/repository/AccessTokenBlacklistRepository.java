package com.meetple.backend.domain.auth.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
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

    private String createKey(String accessToken) {
        return KEY_PREFIX + sha256(accessToken);
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
