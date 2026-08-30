package com.meetple.backend.domain.auth.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccessTokenValidationRepository {

    private static final int BLACKLIST_VALUE_INDEX = 0;
    private static final int REFRESH_TOKEN_VALUE_INDEX = 1;
    private static final int EXPECTED_VALUE_COUNT = 2;

    private final StringRedisTemplate stringRedisTemplate;

    public Status getStatus(String accessToken, Long memberId, String sessionId) {
        List<String> values = stringRedisTemplate.opsForValue().multiGet(List.of(
                AccessTokenBlacklistRepository.createKey(accessToken),
                RefreshTokenRepository.createKey(memberId, sessionId)
        ));
        if (values == null || values.size() != EXPECTED_VALUE_COUNT) {
            throw new IllegalStateException("Access token validation returned an unexpected Redis response.");
        }
        if (values.get(BLACKLIST_VALUE_INDEX) != null) {
            return Status.BLACKLISTED;
        }
        if (values.get(REFRESH_TOKEN_VALUE_INDEX) == null) {
            return Status.INACTIVE_SESSION;
        }
        return Status.ACTIVE;
    }

    public enum Status {
        ACTIVE,
        BLACKLISTED,
        INACTIVE_SESSION
    }
}
