package com.meetple.backend.domain.legal.service;

import com.meetple.backend.domain.auth.dto.request.SignupLegalDocumentRequest;
import com.meetple.backend.domain.legal.dto.response.SignupLegalDocumentResponse;
import com.meetple.backend.domain.legal.entity.LegalDocument;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import com.meetple.backend.domain.legal.entity.MemberLegalRecord;
import com.meetple.backend.domain.legal.repository.LegalDocumentRepository;
import com.meetple.backend.domain.legal.repository.MemberLegalRecordRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.response.ErrorStatus;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalDocumentService {

    private final LegalDocumentRepository legalDocumentRepository;
    private final MemberLegalRecordRepository memberLegalRecordRepository;

    public List<SignupLegalDocumentResponse> getCurrentSignupDocuments() {
        return currentSignupDocuments().stream()
                .map(SignupLegalDocumentResponse::from)
                .toList();
    }

    public List<LegalDocument> resolveCurrentSignupDocuments(
            List<SignupLegalDocumentRequest> requests
    ) {
        if (requests == null || requests.size() != LegalDocumentType.values().length) {
            throw new BadRequestException(ErrorStatus.LEGAL_DOCUMENTS_REQUIRED);
        }

        Set<LegalDocumentType> requestedTypes = requests.stream()
                .map(SignupLegalDocumentRequest::type)
                .collect(Collectors.toSet());
        if (requestedTypes.size() != LegalDocumentType.values().length) {
            throw new BadRequestException(ErrorStatus.LEGAL_DOCUMENTS_REQUIRED);
        }

        Map<LegalDocumentType, LegalDocument> currentByType = currentSignupDocuments().stream()
                .collect(Collectors.toMap(LegalDocument::getType, document -> document));

        boolean allCurrent = requests.stream().allMatch(request -> {
            LegalDocument current = currentByType.get(request.type());
            return current != null && current.getVersion().equals(request.version());
        });
        if (!allCurrent) {
            throw new BadRequestException(ErrorStatus.LEGAL_DOCUMENT_VERSION_INVALID);
        }

        return List.of(LegalDocumentType.values()).stream()
                .map(currentByType::get)
                .toList();
    }

    @Transactional
    public void recordSignup(Member member, List<LegalDocument> documents) {
        List<MemberLegalRecord> records = documents.stream()
                .map(document -> MemberLegalRecord.signup(member, document))
                .toList();
        memberLegalRecordRepository.saveAll(records);
    }

    private List<LegalDocument> currentSignupDocuments() {
        Map<LegalDocumentType, LegalDocument> currentByType = new EnumMap<>(LegalDocumentType.class);
        legalDocumentRepository
                .findAllByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(LocalDateTime.now())
                .forEach(document -> currentByType.putIfAbsent(document.getType(), document));

        if (currentByType.size() != LegalDocumentType.values().length) {
            throw new IllegalStateException("현재 적용할 회원가입 약관이 모두 등록되지 않았습니다.");
        }

        return List.of(LegalDocumentType.values()).stream()
                .map(currentByType::get)
                .toList();
    }
}
