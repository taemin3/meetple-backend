package com.meetple.backend.domain.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.chat.dto.request.SendChatMessageRequest;
import com.meetple.backend.domain.chat.dto.response.ChatMessagePageResponse;
import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import com.meetple.backend.domain.chat.service.ChatService;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        AuthenticatedMember principal = new AuthenticatedMember(1L, "member@meetple.com", MemberRole.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMessagesReturnsCursorMetadata() throws Exception {
        ChatMessageResponse message = messageResponse();
        given(chatService.getMessages(1L, 10L, 20L, null, 50))
                .willReturn(ChatMessagePageResponse.from(List.of(message), true));

        mockMvc.perform(get("/api/v1/chat/rooms/10/messages")
                        .param("beforeSequence", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].roomId").value(10))
                .andExpect(jsonPath("$.data.content[0].sequence").value(7))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.oldestSequence").value(7))
                .andExpect(jsonPath("$.data.latestSequence").value(7));
    }

    @Test
    void sendMessageReturnsPersistedMessage() throws Exception {
        UUID clientMessageId = UUID.randomUUID();
        given(chatService.sendMessage(eq(1L), eq(10L), any(SendChatMessageRequest.class)))
                .willReturn(messageResponse(clientMessageId));

        mockMvc.perform(post("/api/v1/chat/rooms/10/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendChatMessageRequest(clientMessageId, "hello")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientMessageId").value(clientMessageId.toString()))
                .andExpect(jsonPath("$.data.content").value("hello"));
    }

    @Test
    void sendMessageRejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/v1/chat/rooms/10/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientMessageId": "00000000-0000-0000-0000-000000000001",
                                  "content": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("메시지 내용을 입력해주세요."));
    }

    private ChatMessageResponse messageResponse() {
        return messageResponse(UUID.randomUUID());
    }

    private ChatMessageResponse messageResponse(UUID clientMessageId) {
        return new ChatMessageResponse(
                100L,
                10L,
                7L,
                clientMessageId,
                1L,
                "member",
                null,
                "hello",
                LocalDateTime.of(2026, 8, 3, 19, 0)
        );
    }
}
