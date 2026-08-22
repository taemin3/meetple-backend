package com.meetple.backend.domain.legal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.auth.dto.request.SignupLegalDocumentRequest;
import com.meetple.backend.domain.legal.dto.response.SignupLegalDocumentResponse;
import com.meetple.backend.domain.legal.entity.LegalDocument;
import com.meetple.backend.domain.legal.entity.LegalDocumentAction;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import com.meetple.backend.domain.legal.entity.MemberLegalRecord;
import com.meetple.backend.domain.legal.repository.LegalDocumentRepository;
import com.meetple.backend.domain.legal.repository.MemberLegalRecordRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.response.ErrorStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegalDocumentServiceTest {

    @Mock
    private LegalDocumentRepository legalDocumentRepository;

    @Mock
    private MemberLegalRecordRepository memberLegalRecordRepository;

    @InjectMocks
    private LegalDocumentService legalDocumentService;

    private LegalDocument serviceTerms;
    private LegalDocument privacyPolicy;
    private LegalDocument ageConfirmation;

    @BeforeEach
    void setUp() {
        LocalDateTime effectiveAt = LocalDateTime.now().minusDays(1);
        serviceTerms = document(LegalDocumentType.SERVICE_TERMS, "v2", effectiveAt);
        privacyPolicy = document(LegalDocumentType.PRIVACY_POLICY, "v3", effectiveAt);
        ageConfirmation = document(LegalDocumentType.AGE_14_CONFIRMATION, "v1", effectiveAt);
    }

    @Test
    void getCurrentSignupDocumentsReturnsOneCurrentDocumentPerType() {
        givenCurrentDocuments();
        List<SignupLegalDocumentResponse> responses = legalDocumentService.getCurrentSignupDocuments();

        assertThat(responses)
                .extracting(SignupLegalDocumentResponse::type)
                .containsExactly(LegalDocumentType.values());
        assertThat(responses)
                .extracting(SignupLegalDocumentResponse::version)
                .containsExactly("v2", "v3", "v1");
    }

    @Test
    void resolveCurrentSignupDocumentsAcceptsEveryCurrentVersion() {
        givenCurrentDocuments();
        List<LegalDocument> documents = legalDocumentService.resolveCurrentSignupDocuments(validRequests());

        assertThat(documents).containsExactly(serviceTerms, privacyPolicy, ageConfirmation);
    }

    @Test
    void resolveCurrentSignupDocumentsRejectsMissingOrDuplicateTypes() {
        List<SignupLegalDocumentRequest> requests = List.of(
                new SignupLegalDocumentRequest(LegalDocumentType.SERVICE_TERMS, "v2"),
                new SignupLegalDocumentRequest(LegalDocumentType.PRIVACY_POLICY, "v3"),
                new SignupLegalDocumentRequest(LegalDocumentType.PRIVACY_POLICY, "v3")
        );

        assertThatThrownBy(() -> legalDocumentService.resolveCurrentSignupDocuments(requests))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.LEGAL_DOCUMENTS_REQUIRED.getMessage());
    }

    @Test
    void resolveCurrentSignupDocumentsRejectsOutdatedVersion() {
        givenCurrentDocuments();
        List<SignupLegalDocumentRequest> requests = List.of(
                new SignupLegalDocumentRequest(LegalDocumentType.SERVICE_TERMS, "v1"),
                new SignupLegalDocumentRequest(LegalDocumentType.PRIVACY_POLICY, "v3"),
                new SignupLegalDocumentRequest(LegalDocumentType.AGE_14_CONFIRMATION, "v1")
        );

        assertThatThrownBy(() -> legalDocumentService.resolveCurrentSignupDocuments(requests))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorStatus.LEGAL_DOCUMENT_VERSION_INVALID.getMessage());
    }

    @Test
    void recordSignupStoresServerDefinedActions() {
        Member member = Member.createUser("user@meetple.com", "password", "tester", null);

        legalDocumentService.recordSignup(member, List.of(serviceTerms, privacyPolicy, ageConfirmation));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemberLegalRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(memberLegalRecordRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(MemberLegalRecord::getAction)
                .containsExactly(
                        LegalDocumentAction.ACCEPTED,
                        LegalDocumentAction.ACKNOWLEDGED,
                        LegalDocumentAction.CONFIRMED
                );
    }

    private List<SignupLegalDocumentRequest> validRequests() {
        return List.of(
                new SignupLegalDocumentRequest(LegalDocumentType.SERVICE_TERMS, "v2"),
                new SignupLegalDocumentRequest(LegalDocumentType.PRIVACY_POLICY, "v3"),
                new SignupLegalDocumentRequest(LegalDocumentType.AGE_14_CONFIRMATION, "v1")
        );
    }

    private void givenCurrentDocuments() {
        given(legalDocumentRepository.findAllByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(any()))
                .willReturn(List.of(serviceTerms, privacyPolicy, ageConfirmation));
    }

    private LegalDocument document(LegalDocumentType type, String version, LocalDateTime effectiveAt) {
        return LegalDocument.create(type, version, type.name(), type.name(), effectiveAt);
    }
}
