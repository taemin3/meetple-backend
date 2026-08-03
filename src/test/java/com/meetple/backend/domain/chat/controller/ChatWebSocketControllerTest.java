package com.meetple.backend.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.chat.dto.request.SendChatMessageRequest;
import com.meetple.backend.domain.chat.service.ChatService;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatWebSocketController controller;

    @Test
    void sendMessageDelegatesPersistenceAndPublicationToChatService() {
        UUID clientMessageId = UUID.randomUUID();
        SendChatMessageRequest request = new SendChatMessageRequest(clientMessageId, "안녕하세요");

        controller.sendMessage(10L, request, authentication());

        verify(chatService).sendMessage(1L, 10L, request);
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
