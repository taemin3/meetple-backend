package com.meetple.backend.domain.chat.controller;

import com.meetple.backend.domain.chat.dto.request.SendChatMessageRequest;
import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import com.meetple.backend.domain.chat.dto.response.ChatWebSocketErrorResponse;
import com.meetple.backend.domain.chat.service.ChatService;
import com.meetple.backend.global.exception.BaseException;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
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
            SendChatMessageRequest request,
            Principal principal
    ) {
        AuthenticatedMember member = authenticatedMember(principal);
        ChatMessageResponse savedMessage = chatService.sendMessage(member.id(), roomId, request);
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + roomId, savedMessage);
    }

    @MessageExceptionHandler(BaseException.class)
    @SendToUser(destinations = "/queue/chat/errors", broadcast = false)
    public ChatWebSocketErrorResponse handleBaseException(BaseException exception) {
        return new ChatWebSocketErrorResponse(
                exception.getStatusCode(),
                false,
                exception.getErrorCode(),
                exception.getResponseMessage()
        );
    }

    @MessageExceptionHandler(AuthenticationException.class)
    @SendToUser(destinations = "/queue/chat/errors", broadcast = false)
    public ChatWebSocketErrorResponse handleAuthenticationException() {
        ErrorStatus errorStatus = ErrorStatus.INVALID_TOKEN;
        return new ChatWebSocketErrorResponse(
                errorStatus.getStatusCode(),
                false,
                errorStatus.getCode(),
                errorStatus.getMessage()
        );
    }

    @MessageExceptionHandler(RuntimeException.class)
    @SendToUser(destinations = "/queue/chat/errors", broadcast = false)
    public ChatWebSocketErrorResponse handleRuntimeException(RuntimeException exception) {
        log.error("STOMP 채팅 메시지 처리 중 오류가 발생했습니다.", exception);
        ErrorStatus errorStatus = ErrorStatus.INTERNAL_SERVER_ERROR;
        return new ChatWebSocketErrorResponse(
                errorStatus.getStatusCode(),
                false,
                errorStatus.getCode(),
                errorStatus.getMessage()
        );
    }

    private AuthenticatedMember authenticatedMember(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedMember member) {
            return member;
        }
        throw new BadCredentialsException(ErrorStatus.INVALID_TOKEN.getMessage());
    }
}
