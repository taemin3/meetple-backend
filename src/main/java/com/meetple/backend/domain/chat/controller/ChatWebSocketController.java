package com.meetple.backend.domain.chat.controller;

import com.meetple.backend.domain.chat.dto.request.SendChatMessageRequest;
import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import com.meetple.backend.domain.chat.service.ChatMessageSendResult;
import com.meetple.backend.domain.chat.service.ChatService;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private static final String ROOM_TOPIC_PREFIX = "/topic/chat/rooms/";

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Valid SendChatMessageRequest request,
            Principal principal
    ) {
        AuthenticatedMember member = authenticatedMember(principal);
        ChatMessageSendResult result = chatService.sendMessage(member.id(), roomId, request);
        if (result.created()) {
            ApiResponse<ChatMessageResponse> response = ApiResponse.successBody(
                    SuccessStatus.OK,
                    result.message()
            );
            messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomId, response);
        }
    }

    @MessageExceptionHandler(BaseException.class)
    @SendToUser(destinations = "/queue/chat/errors", broadcast = false)
    public ApiResponse<Void> handleBaseException(BaseException exception) {
        return ApiResponse.errorBody(
                exception.getStatusCode(),
                exception.getErrorCode(),
                exception.getResponseMessage()
        );
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = "/queue/chat/errors", broadcast = false)
    public ApiResponse<Void> handleValidationException() {
        return ApiResponse.errorBody(ErrorStatus.VALIDATION_ERROR);
    }

    @MessageExceptionHandler(AuthenticationException.class)
    @SendToUser(destinations = "/queue/chat/errors", broadcast = false)
    public ApiResponse<Void> handleAuthenticationException() {
        return ApiResponse.errorBody(ErrorStatus.INVALID_TOKEN);
    }

    @MessageExceptionHandler(RuntimeException.class)
    @SendToUser(destinations = "/queue/chat/errors", broadcast = false)
    public ApiResponse<Void> handleRuntimeException(RuntimeException exception) {
        log.error("STOMP 채팅 메시지 처리 중 오류가 발생했습니다.", exception);
        return ApiResponse.errorBody(ErrorStatus.INTERNAL_SERVER_ERROR);
    }

    private AuthenticatedMember authenticatedMember(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedMember member) {
            return member;
        }
        throw new BadCredentialsException(ErrorStatus.INVALID_TOKEN.getMessage());
    }
}
