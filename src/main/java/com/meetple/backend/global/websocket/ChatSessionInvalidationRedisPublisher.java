package com.meetple.backend.global.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionInvalidationRedisPublisher {

    public static final String CHANNEL = "meetple:chat:session-invalidation:v1";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(ChatSessionInvalidationEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException exception) {
            log.error(
                    "채팅 세션 무효화 Redis 이벤트를 발행하지 못했습니다. eventId={}",
                    event.eventId(),
                    exception
            );
        }
    }
}
