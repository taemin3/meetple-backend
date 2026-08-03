package com.meetple.backend.global.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable exception
    ) {
        ApiResponse<Void> response = toErrorResponse(exception);
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
            accessor.setMessage(response.getMessage());
            accessor.setContentType(MediaType.APPLICATION_JSON);
            accessor.setLeaveMutable(true);
            return MessageBuilder.createMessage(
                    objectMapper.writeValueAsBytes(response),
                    accessor.getMessageHeaders()
            );
        } catch (JsonProcessingException serializationException) {
            log.error("STOMP 오류 응답 직렬화에 실패했습니다.", serializationException);
            return super.handleClientMessageProcessingError(clientMessage, exception);
        }
    }

    private ApiResponse<Void> toErrorResponse(Throwable exception) {
        Throwable cause = findKnownCause(exception);
        if (cause instanceof BaseException baseException) {
            return ApiResponse.errorBody(
                    baseException.getStatusCode(),
                    baseException.getErrorCode(),
                    baseException.getResponseMessage()
            );
        }
        if (cause instanceof MethodArgumentNotValidException) {
            return ApiResponse.errorBody(ErrorStatus.VALIDATION_ERROR);
        }
        if (cause instanceof BadCredentialsException) {
            return ApiResponse.errorBody(ErrorStatus.INVALID_TOKEN);
        }
        if (cause instanceof AccessDeniedException) {
            return ApiResponse.errorBody(ErrorStatus.ACCESS_DENIED);
        }

        log.error("STOMP 프레임 처리 중 오류가 발생했습니다.", exception);
        return ApiResponse.errorBody(ErrorStatus.INTERNAL_SERVER_ERROR);
    }

    private Throwable findKnownCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof BaseException
                    || current instanceof MethodArgumentNotValidException
                    || current instanceof BadCredentialsException
                    || current instanceof AccessDeniedException) {
                return current;
            }
            current = current.getCause();
        }
        return exception;
    }

}
