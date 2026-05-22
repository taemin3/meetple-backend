package com.meetple.backend.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.auth.dto.request.LoginRequest;
import com.meetple.backend.domain.auth.dto.request.SignupRequest;
import com.meetple.backend.domain.auth.dto.response.AuthMemberResponse;
import com.meetple.backend.domain.auth.dto.response.LoginResponse;
import com.meetple.backend.domain.auth.service.AuthService;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import com.meetple.backend.global.response.SuccessStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signupReturnsCreatedApiResponse() throws Exception {
        given(authService.signup(any(SignupRequest.class)))
                .willReturn(new AuthMemberResponse(1L, "user@meetple.com", "tester", null));

        SignupRequest request = new SignupRequest("user@meetple.com", "password123", "tester");

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
    void loginReturnsOkApiResponseWithAccessToken() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willReturn(LoginResponse.bearer("access-token", 3600L));

        LoginRequest request = new LoginRequest("user@meetple.com", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }
}
