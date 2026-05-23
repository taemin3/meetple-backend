package com.meetple.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RedisConnectionConfigTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void stringRedisTemplateBeanIsConfigured() {
        assertThat(stringRedisTemplate).isNotNull();
        assertThat(stringRedisTemplate.getConnectionFactory()).isNotNull();
    }
}
