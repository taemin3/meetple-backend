package com.meetple.backend.domain.chat.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void sendMessageIsNotExposedAsRestEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/chat/rooms/10/messages"))
                .andExpect(status().isMethodNotAllowed());
    }

    private ChatMessageResponse messageResponse() {
        return new ChatMessageResponse(
                100L,
                10L,
                7L,
                UUID.randomUUID(),
                1L,
                "member",
                null,
                "hello",
                LocalDateTime.of(2026, 8, 3, 19, 0)
        );
    }
}
