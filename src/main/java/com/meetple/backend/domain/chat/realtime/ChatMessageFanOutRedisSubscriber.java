package com.meetple.backend.domain.chat.realtime;

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
public class ChatMessageFanOutRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ChatMessageFanOutService fanOutService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatMessageFanOutEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    ChatMessageFanOutEvent.class
            );
            fanOutService.fanOutToLocalSubscribers(event);
        } catch (Exception exception) {
            log.error("채팅 메시지 Redis fan-out 이벤트를 처리하지 못했습니다.", exception);
        }
    }
}
