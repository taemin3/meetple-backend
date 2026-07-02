package com.meetple.backend.domain.image.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.image.dto.response.ImageUploadUrlResponse;
import com.meetple.backend.domain.image.service.ImageService;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.global.exception.GlobalExceptionHandler;
import com.meetple.backend.global.response.ErrorStatus;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    private static final AuthenticatedMember AUTHENTICATED_MEMBER = new AuthenticatedMember(
            1L,
            "user@meetple.com",
            MemberRole.USER
    );

    @Mock
    private ImageService imageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ImageController(imageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticatedMemberArgumentResolver())
                .build();
    }

    @Test
    void createUploadUrlReturnsApiResponse() throws Exception {
        given(imageService.createUploadUrl(eq(1L), any()))
                .willReturn(new ImageUploadUrlResponse(
                        "https://upload.meetple.com/images/profile/1/image.png",
                        "https://cdn.meetple.com/images/profile/1/image.png",
                        "images/profile/1/image.png",
                        "PUT",
                        Map.of("Content-Type", "image/png"),
                        300L
                ));

        mockMvc.perform(post("/api/v1/images/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "purpose": "PROFILE",
                                  "fileName": "avatar.png",
                                  "contentType": "image/png",
                                  "contentLength": 512
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://upload.meetple.com/images/profile/1/image.png"))
                .andExpect(jsonPath("$.data.fileUrl").value("https://cdn.meetple.com/images/profile/1/image.png"))
                .andExpect(jsonPath("$.data.objectKey").value("images/profile/1/image.png"))
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andExpect(jsonPath("$.data.headers.Content-Type").value("image/png"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
    }

    @Test
    void createUploadUrlWithoutRequiredFieldReturnsValidationResponse() throws Exception {
        mockMvc.perform(post("/api/v1/images/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "purpose": "PROFILE",
                                  "fileName": "avatar.png",
                                  "contentType": "image/png"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.VALIDATION_ERROR.getCode()));

        verifyNoInteractions(imageService);
    }

    @Test
    void createUploadUrlWithInvalidPurposeReturnsValidationResponse() throws Exception {
        mockMvc.perform(post("/api/v1/images/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "purpose": "AVATAR",
                                  "fileName": "avatar.png",
                                  "contentType": "image/png",
                                  "contentLength": 512
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorStatus.VALIDATION_ERROR.getCode()));

        verifyNoInteractions(imageService);
    }

    private static class AuthenticatedMemberArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && parameter.getParameterType().equals(AuthenticatedMember.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                org.springframework.web.bind.support.WebDataBinderFactory binderFactory
        ) {
            return AUTHENTICATED_MEMBER;
        }
    }
}
