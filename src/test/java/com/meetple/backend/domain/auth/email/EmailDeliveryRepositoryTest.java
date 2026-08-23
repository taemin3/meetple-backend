package com.meetple.backend.domain.auth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryRepositoryTest {

    private static final UUID DELIVERY_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final String PAYLOAD_KEY = "email-delivery:payload:" + DELIVERY_ID;
    private static final String CLAIM_KEY = "email-delivery:claim:" + DELIVERY_ID;

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmailDeliveryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EmailDeliveryRepository(stringRedisTemplate);
    }

    @Test
    void saveStoresSensitivePayloadOnlyUnderRandomDeliveryIdWithTtl() {
        given(stringRedisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(PAYLOAD_KEY)),
                eq("SIGNUP_VERIFICATION"),
                eq("user@meetple.com"),
                eq("123456"),
                eq("code-hash"),
                eq("true"),
                eq("300000")
        )).willReturn(1L);

        repository.save(delivery(EmailDeliveryPurpose.SIGNUP_VERIFICATION, true), Duration.ofMinutes(5));

        assertThat(PAYLOAD_KEY).doesNotContain("user@meetple.com", "123456");
    }

    @Test
    void saveRejectsMissingRedisResult() {
        assertThatThrownBy(() -> repository.save(
                delivery(EmailDeliveryPurpose.SIGNUP_VERIFICATION, true),
                Duration.ofMinutes(5)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findRestoresPendingDelivery() {
        given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(PAYLOAD_KEY)).willReturn(Map.of(
                "purpose", "PASSWORD_RESET",
                "recipient", "user@meetple.com",
                "code", "123456",
                "codeHash", "code-hash",
                "deliver", "false"
        ));

        assertThat(repository.find(DELIVERY_ID)).contains(
                delivery(EmailDeliveryPurpose.PASSWORD_RESET, false)
        );
    }

    @Test
    void invalidStoredPayloadIsDeleted() {
        given(stringRedisTemplate.opsForHash()).willReturn(hashOperations);
        given(hashOperations.entries(PAYLOAD_KEY)).willReturn(Map.of(
                "purpose", "UNKNOWN",
                "recipient", "user@meetple.com",
                "code", "123456",
                "codeHash", "code-hash",
                "deliver", "true"
        ));

        assertThat(repository.find(DELIVERY_ID)).isEmpty();

        verify(stringRedisTemplate).delete(List.of(PAYLOAD_KEY, CLAIM_KEY));
    }

    @Test
    void claimUsesShortLivedDedicatedKey() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(CLAIM_KEY, "1", Duration.ofSeconds(30)))
                .willReturn(true);

        assertThat(repository.tryClaim(DELIVERY_ID)).isTrue();
    }

    private PendingEmailDelivery delivery(EmailDeliveryPurpose purpose, boolean deliver) {
        return new PendingEmailDelivery(
                DELIVERY_ID,
                purpose,
                "user@meetple.com",
                "123456",
                "code-hash",
                deliver
        );
    }
}
