package com.meetple.backend.global.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionInvalidationRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ChatSessionInvalidationService invalidationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatSessionInvalidationEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    ChatSessionInvalidationEvent.class
            );
            invalidationService.invalidateLocalSessions(event);
        } catch (Exception exception) {
            log.error("채팅 세션 무효화 Redis 이벤트를 처리하지 못했습니다.", exception);
        }
    }
}
