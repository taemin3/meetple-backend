package com.meetple.backend.domain.legal.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meetple.backend.domain.legal.dto.response.SignupLegalDocumentResponse;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import com.meetple.backend.domain.legal.service.LegalDocumentService;
import com.meetple.backend.global.response.SuccessStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LegalDocumentControllerTest {

    @Mock
    private LegalDocumentService legalDocumentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new LegalDocumentController(legalDocumentService)
        ).build();
    }

    @Test
    void getSignupDocumentsReturnsCurrentDocuments() throws Exception {
        given(legalDocumentService.getCurrentSignupDocuments()).willReturn(List.of(
                new SignupLegalDocumentResponse(
                        LegalDocumentType.SERVICE_TERMS,
                        "2026-08-22",
                        "서비스 이용약관",
                        "내용",
                        LocalDateTime.of(2026, 8, 22, 0, 0)
                )
        ));

        mockMvc.perform(get("/api/v1/legal-documents/signup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(SuccessStatus.OK.getCode()))
                .andExpect(jsonPath("$.data[0].type").value("SERVICE_TERMS"))
                .andExpect(jsonPath("$.data[0].version").value("2026-08-22"));
    }
}
