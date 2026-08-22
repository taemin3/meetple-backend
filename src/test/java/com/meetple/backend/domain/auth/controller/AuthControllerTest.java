package com.meetple.backend.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationConfirmRequest;
import com.meetple.backend.domain.auth.dto.request.EmailVerificationSendRequest;
import com.meetple.backend.domain.auth.dto.request.LoginRequest;
import com.meetple.backend.domain.auth.dto.request.LogoutRequest;
import com.meetple.backend.domain.auth.dto.request.PasswordResetRequest;
import com.meetple.backend.domain.auth.dto.request.ReissueRequest;
import com.meetple.backend.domain.auth.dto.request.SignupLegalDocumentRequest;
import com.meetple.backend.domain.auth.dto.request.SignupRequest;
import com.meetple.backend.domain.auth.dto.response.AuthMemberResponse;
import com.meetple.backend.domain.auth.dto.response.EmailVerificationConfirmResponse;
import com.meetple.backend.domain.auth.dto.response.LoginResponse;
import com.meetple.backend.domain.auth.dto.response.PasswordResetVerificationResponse;
import com.meetple.backend.domain.auth.service.AuthService;
import com.meetple.backend.domain.auth.service.EmailVerificationService;
import com.meetple.backend.domain.auth.service.PasswordResetService;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import com.meetple.backend.global.response.SuccessStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordResetService passwordResetService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
                mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                        authService,
                        emailVerificationService,
                        passwordResetService
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendEmailVerificationCodeReturnsOk() throws Exception {
        EmailVerificationSendRequest request = new EmailVerificationSendRequest(
                "user@meetple.com"
        );

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailVerificationService).sendVerificationCode(request, "127.0.0.1");
    }

    @Test
    void confirmEmailVerificationCodeReturnsSignupToken() throws Exception {
        EmailVerificationConfirmRequest request = new EmailVerificationConfirmRequest(
                "user@meetple.com",
                "123456"
        );
        given(emailVerificationService.confirm(
                any(EmailVerificationConfirmRequest.class),
                eq("127.0.0.1")
        ))
                .willReturn(new EmailVerificationConfirmResponse("signup-token", 900));

        mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signupVerificationToken").value("signup-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void confirmEmailVerificationCodeRejectsMalformedCode() throws Exception {
        EmailVerificationConfirmRequest request = new EmailVerificationConfirmRequest(
                "user@meetple.com",
                "12345"
        );

        mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void sendPasswordResetVerificationCodeReturnsGenericOk() throws Exception {
        EmailVerificationSendRequest request = new EmailVerificationSendRequest(
                "user@meetple.com"
        );

        mockMvc.perform(post("/api/v1/auth/password-resets/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(passwordResetService).sendVerificationCode(request, "127.0.0.1");
    }

    @Test
    void confirmPasswordResetVerificationCodeReturnsResetToken() throws Exception {
        EmailVerificationConfirmRequest request = new EmailVerificationConfirmRequest(
                "user@meetple.com",
                "123456"
        );
        given(passwordResetService.confirm(
                any(EmailVerificationConfirmRequest.class),
                eq("127.0.0.1")
        )).willReturn(new PasswordResetVerificationResponse("reset-token", 900));

        mockMvc.perform(post("/api/v1/auth/password-resets/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordResetToken").value("reset-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void resetPasswordReturnsOk() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest(
                "user@meetple.com",
                "reset-token",
                "new-password123"
        );

        mockMvc.perform(post("/api/v1/auth/password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(passwordResetService).resetPassword(request);
    }

    @Test
    void signupReturnsCreatedApiResponse() throws Exception {
        given(authService.signup(any(SignupRequest.class)))
                .willReturn(new AuthMemberResponse(1L, "user@meetple.com", "tester", null));

        SignupRequest request = new SignupRequest(
                "user@meetple.com",
                "signup-verification-token",
                "password123",
                "tester",
                List.of(
                        new SignupLegalDocumentRequest(LegalDocumentType.SERVICE_TERMS, "2026-08-22"),
                        new SignupLegalDocumentRequest(LegalDocumentType.PRIVACY_POLICY, "2026-08-22"),
                        new SignupLegalDocumentRequest(LegalDocumentType.AGE_14_CONFIRMATION, "2026-08-22")
                )
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.CREATED.getCode()))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("user@meetple.com"))
                .andExpect(jsonPath("$.data.nickname").value("tester"));
    }

    @Test
    void signupRejectsNullLegalDocumentItem() throws Exception {
        String request = """
                {
                  "email": "user@meetple.com",
                  "signupVerificationToken": "signup-verification-token",
                  "password": "password123",
                  "nickname": "tester",
                  "legalDocuments": [
                    null,
                    {"type": "PRIVACY_POLICY", "version": "2026-08-22"},
                    {"type": "AGE_14_CONFIRMATION", "version": "2026-08-22"}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void loginReturnsOkApiResponseWithAccessToken() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willReturn(LoginResponse.bearer("access-token", "refresh-token", 3600L, 1209600L));

        LoginRequest request = new LoginRequest("user@meetple.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(3600))
                .andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600));
    }

    @Test
    void reissueReturnsOkApiResponseWithRotatedTokens() throws Exception {
        given(authService.reissue(any(ReissueRequest.class)))
                .willReturn(LoginResponse.bearer("new-access-token", "new-refresh-token", 3600L, 1209600L));

        ReissueRequest request = new ReissueRequest("refresh-token");

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void logoutReturnsOkApiResponse() throws Exception {
        LogoutRequest request = new LogoutRequest("refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()));

        verify(authService).logout(any(LogoutRequest.class), eq("Bearer access-token"));
    }

    @Test
    void logoutAllReturnsOkApiResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()));

        verify(authService).logoutAll("Bearer access-token");
    }
}
