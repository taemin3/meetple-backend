package com.meetple.backend.domain.auth.repository;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    public void save(Long memberId, String refreshToken, Duration ttl) {
        stringRedisTemplate.opsForValue().set(createKey(memberId), refreshToken, ttl);
    }

    public Optional<String> findByMemberId(Long memberId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(createKey(memberId)));
    }

    public void deleteByMemberId(Long memberId) {
        stringRedisTemplate.delete(createKey(memberId));
    }

    private String createKey(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
