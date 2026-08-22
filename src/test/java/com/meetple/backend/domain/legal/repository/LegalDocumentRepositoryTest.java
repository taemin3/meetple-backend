package com.meetple.backend.domain.legal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.legal.entity.LegalDocument;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class LegalDocumentRepositoryTest {

    @Autowired
    private LegalDocumentRepository legalDocumentRepository;

    @Test
    void currentDocumentsUseNewestIdWhenEffectiveAtIsEqual() {
        LocalDateTime effectiveAt = LocalDateTime.now().minusDays(1);
        LegalDocument first = legalDocumentRepository.saveAndFlush(document("v1", effectiveAt));
        LegalDocument second = legalDocumentRepository.saveAndFlush(document("v2", effectiveAt));

        assertThat(legalDocumentRepository
                .findAllByEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(LocalDateTime.now()))
                .containsExactly(second, first);
    }

    private LegalDocument document(String version, LocalDateTime effectiveAt) {
        return LegalDocument.create(
                LegalDocumentType.SERVICE_TERMS,
                version,
                "서비스 이용약관",
                "내용",
                effectiveAt
        );
    }
}
