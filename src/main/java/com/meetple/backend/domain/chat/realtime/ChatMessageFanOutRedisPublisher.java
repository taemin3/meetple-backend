package com.meetple.backend.domain.chat.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatMessageFanOutRedisPublisher {

    public static final String CHANNEL = "meetple:chat:message-fan-out:v1";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean redisEnabled;

    public ChatMessageFanOutRedisPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${chat.message-fan-out.redis-enabled:true}") boolean redisEnabled
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisEnabled = redisEnabled;
    }

    public void publish(ChatMessageFanOutEvent event) {
        if (!redisEnabled) {
            return;
        }
        try {
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException exception) {
            log.error(
                    "채팅 메시지 Redis fan-out 이벤트를 발행하지 못했습니다. eventId={}, roomId={}",
                    event.eventId(),
                    event.message().roomId(),
                    exception
            );
        }
    }
}
