package com.meetple.backend.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.auth.dto.request.AccountDeletionCompleteRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.response.AccountDeletionVerificationResponse;
import com.meetple.backend.domain.member.service.AccountDeletionService;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AccountDeletionControllerTest {

    @Mock
    private AccountDeletionService service;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountDeletionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendCodeReturnsSameSuccessfulEnvelope() throws Exception {
        EmailVerificationSendRequest request = new EmailVerificationSendRequest("user@meetple.com");

        mockMvc.perform(post("/api/v1/account-deletions/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).sendVerificationCode(request, "127.0.0.1");
    }

    @Test
    void confirmCodeReturnsPurposeSpecificOneTimeToken() throws Exception {
        given(service.confirm(any(EmailVerificationConfirmRequest.class), eq("127.0.0.1")))
                .willReturn(new AccountDeletionVerificationResponse("deletion-token", 900));

        mockMvc.perform(post("/api/v1/account-deletions/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@meetple.com","code":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountDeletionToken").value("deletion-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void finalDeletionRequiresExplicitConfirmation() throws Exception {
        mockMvc.perform(post("/api/v1/account-deletions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"user@meetple.com",
                                  "accountDeletionToken":"deletion-token",
                                  "confirmed":false
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void finalDeletionAcceptsVerifiedRequest() throws Exception {
        AccountDeletionCompleteRequest request = new AccountDeletionCompleteRequest(
                "user@meetple.com",
                "deletion-token",
                true
        );

        mockMvc.perform(post("/api/v1/account-deletions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).deleteWithVerifiedEmail(request);
    }
}
