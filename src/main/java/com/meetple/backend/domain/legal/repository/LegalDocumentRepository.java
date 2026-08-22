package com.meetple.backend.domain.legal.repository;

import com.meetple.backend.domain.legal.entity.LegalDocument;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, Long> {

    List<LegalDocument> findAllByEffectiveAtLessThanEqualOrderByEffectiveAtDesc(LocalDateTime effectiveAt);

    boolean existsByTypeAndVersion(LegalDocumentType type, String version);
}
