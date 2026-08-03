package com.meetple.backend.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.meetple.backend.domain.chat.dto.request.SendChatMessageRequest;
import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import com.meetple.backend.domain.chat.service.ChatService;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatWebSocketController controller;

    @Test
    void sendMessageBroadcastsOnlyPersistedMessage() {
        UUID clientMessageId = UUID.randomUUID();
        SendChatMessageRequest request = new SendChatMessageRequest(clientMessageId, "안녕하세요");
        ChatMessageResponse savedMessage = new ChatMessageResponse(
                100L,
                10L,
                7L,
                clientMessageId,
                1L,
                "member",
                null,
                "안녕하세요",
                LocalDateTime.of(2026, 8, 4, 0, 0)
        );
        given(chatService.sendMessage(1L, 10L, request)).willReturn(savedMessage);

        controller.sendMessage(10L, request, authentication());

        InOrder inOrder = inOrder(chatService, messagingTemplate);
        inOrder.verify(chatService).sendMessage(1L, 10L, request);
        inOrder.verify(messagingTemplate).convertAndSend(
                "/topic/chat/rooms/10",
                savedMessage
        );
    }

    @Test
    void baseExceptionIsConvertedToWebSocketErrorResponse() {
        var response = controller.handleBaseException(new ForbiddenException("입장할 수 없습니다."));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(10301);
        assertThat(response.getMessage()).isEqualTo("입장할 수 없습니다.");
    }

    @Test
    void sendMessageRejectsUnknownPrincipal() {
        SendChatMessageRequest request = new SendChatMessageRequest(
                UUID.randomUUID(),
                "안녕하세요"
        );

        assertThatThrownBy(() -> controller.sendMessage(10L, request, () -> "unknown"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("유효하지 않은 토큰입니다.");
    }

    @Test
    void authenticationExceptionIsConvertedToInvalidTokenResponse() {
        var response = controller.handleAuthenticationException();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(12410);
        assertThat(response.getMessage()).isEqualTo("유효하지 않은 토큰입니다.");
    }

    @Test
    void validationExceptionIsConvertedToValidationErrorResponse() {
        var response = controller.handleValidationException();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(10004);
    }

    private Authentication authentication() {
        AuthenticatedMember member = new AuthenticatedMember(
                1L,
                "member@meetple.com",
                MemberRole.USER
        );
        return new UsernamePasswordAuthenticationToken(member, null, List.of());
    }
}
