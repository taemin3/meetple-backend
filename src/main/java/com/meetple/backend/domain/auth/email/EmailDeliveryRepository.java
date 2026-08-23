package com.meetple.backend.domain.auth.email;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmailDeliveryRepository {

    private static final String DELIVERY_KEY_PREFIX = "email-delivery:payload:";
    private static final String CLAIM_KEY_PREFIX = "email-delivery:claim:";
    private static final Duration CLAIM_TTL = Duration.ofSeconds(30);
    private static final RedisScript<Long> SAVE_SCRIPT = createScript("""
            redis.call('HSET', KEYS[1],
                'purpose', ARGV[1],
                'recipient', ARGV[2],
                'code', ARGV[3],
                'codeHash', ARGV[4],
                'deliver', ARGV[5])
            redis.call('PEXPIRE', KEYS[1], ARGV[6])
            return 1
            """);

    private final StringRedisTemplate stringRedisTemplate;

    public void save(PendingEmailDelivery delivery, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Email delivery TTL must be positive.");
        }
        Long result = stringRedisTemplate.execute(
                SAVE_SCRIPT,
                List.of(createDeliveryKey(delivery.deliveryId())),
                delivery.purpose().name(),
                delivery.recipient(),
                delivery.code(),
                delivery.codeHash(),
                Boolean.toString(delivery.deliver()),
                Long.toString(ttl.toMillis())
        );
        if (!Long.valueOf(1L).equals(result)) {
            throw new IllegalStateException("Failed to save email delivery payload.");
        }
    }

    public Optional<PendingEmailDelivery> find(UUID deliveryId) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash()
                .entries(createDeliveryKey(deliveryId));
        if (values.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PendingEmailDelivery(
                    deliveryId,
                    EmailDeliveryPurpose.valueOf(required(values, "purpose")),
                    required(values, "recipient"),
                    required(values, "code"),
                    required(values, "codeHash"),
                    requiredBoolean(values, "deliver")
            ));
        } catch (IllegalArgumentException exception) {
            delete(deliveryId);
            return Optional.empty();
        }
    }

    public boolean tryClaim(UUID deliveryId) {
        Boolean claimed = stringRedisTemplate.opsForValue().setIfAbsent(
                createClaimKey(deliveryId),
                "1",
                CLAIM_TTL
        );
        return Boolean.TRUE.equals(claimed);
    }

    public void releaseClaim(UUID deliveryId) {
        stringRedisTemplate.delete(createClaimKey(deliveryId));
    }

    public void delete(UUID deliveryId) {
        stringRedisTemplate.delete(List.of(
                createDeliveryKey(deliveryId),
                createClaimKey(deliveryId)
        ));
    }

    private String required(Map<Object, Object> values, String fieldName) {
        Object value = values.get(fieldName);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Email delivery payload is invalid.");
        }
        return text;
    }

    private boolean requiredBoolean(Map<Object, Object> values, String fieldName) {
        String value = required(values, fieldName);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("Email delivery payload is invalid.");
        }
        return Boolean.parseBoolean(value);
    }

    private String createDeliveryKey(UUID deliveryId) {
        return DELIVERY_KEY_PREFIX + deliveryId;
    }

    private String createClaimKey(UUID deliveryId) {
        return CLAIM_KEY_PREFIX + deliveryId;
    }

    private static RedisScript<Long> createScript(String scriptText) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(scriptText);
        redisScript.setResultType(Long.class);
        return redisScript;
    }
}
