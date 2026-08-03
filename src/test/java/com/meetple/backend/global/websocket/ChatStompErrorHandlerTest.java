package com.meetple.backend.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.chat.dto.response.ChatWebSocketErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class ChatStompErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatStompErrorHandler errorHandler = new ChatStompErrorHandler(objectMapper);

    @Test
    void authenticationFailureReturnsKoreanInvalidTokenErrorFrame() throws Exception {
        var message = errorHandler.handleClientMessageProcessingError(
                null,
                new RuntimeException(
                        "failed",
                        new BadCredentialsException("invalid")
                )
        );

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        ChatWebSocketErrorResponse response = objectMapper.readValue(
                message.getPayload(),
                ChatWebSocketErrorResponse.class
        );
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo(12410);
        assertThat(response.message()).isEqualTo("유효하지 않은 토큰입니다.");
    }

    @Test
    void accessFailureReturnsForbiddenErrorFrame() throws Exception {
        var message = errorHandler.handleClientMessageProcessingError(
                null,
                new AccessDeniedException("denied")
        );

        ChatWebSocketErrorResponse response = objectMapper.readValue(
                message.getPayload(),
                ChatWebSocketErrorResponse.class
        );
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.code()).isEqualTo(10302);
        assertThat(response.message()).isEqualTo("접근 권한이 없어 접근이 거부되었습니다.");
    }
}
