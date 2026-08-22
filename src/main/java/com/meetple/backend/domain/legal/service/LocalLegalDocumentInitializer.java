package com.meetple.backend.domain.legal.service;

import com.meetple.backend.domain.legal.entity.LegalDocument;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import com.meetple.backend.domain.legal.repository.LegalDocumentRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalLegalDocumentInitializer implements ApplicationRunner {

    private static final String INITIAL_VERSION = "2026-08-22";
    private static final LocalDateTime INITIAL_EFFECTIVE_AT = LocalDateTime.of(2026, 8, 22, 0, 0);

    private final LegalDocumentRepository legalDocumentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialDocuments().stream()
                .filter(document -> !legalDocumentRepository.existsByTypeAndVersion(
                        document.getType(),
                        document.getVersion()
                ))
                .forEach(legalDocumentRepository::save);
    }

    private List<LegalDocument> initialDocuments() {
        return List.of(
                LegalDocument.create(
                        LegalDocumentType.SERVICE_TERMS,
                        INITIAL_VERSION,
                        "서비스 이용약관",
                        "밋플 서비스는 사용자가 모임을 만들고 참여할 수 있도록 지원합니다. "
                                + "사용자는 타인의 권리를 침해하거나 서비스 운영을 방해해서는 안 되며, "
                                + "운영정책을 위반한 콘텐츠와 계정은 제한될 수 있습니다. "
                                + "회사는 안정적인 서비스 제공을 위해 기능을 변경할 수 있고 "
                                + "중요한 변경은 서비스 내에서 안내합니다.",
                        INITIAL_EFFECTIVE_AT
                ),
                LegalDocument.create(
                        LegalDocumentType.PRIVACY_POLICY,
                        INITIAL_VERSION,
                        "개인정보 처리방침",
                        "밋플은 회원가입과 서비스 제공을 위해 이메일, 비밀번호의 암호화 값, 닉네임을 "
                                + "필수로 처리하며 프로필 사진과 한줄 소개는 선택적으로 처리합니다. "
                                + "개인정보는 회원 탈퇴 시까지 보관하되 관계 법령에 따라 보존할 의무가 "
                                + "있는 경우 해당 기간 동안 분리 보관합니다. "
                                + "사용자는 자신의 개인정보를 조회·수정하고 회원 탈퇴를 요청할 수 있습니다.",
                        INITIAL_EFFECTIVE_AT
                ),
                LegalDocument.create(
                        LegalDocumentType.AGE_14_CONFIRMATION,
                        INITIAL_VERSION,
                        "만 14세 이상 확인",
                        "회원가입을 진행하는 사용자는 만 14세 이상임을 확인합니다.",
                        INITIAL_EFFECTIVE_AT
                )
        );
    }
}
