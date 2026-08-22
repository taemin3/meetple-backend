package com.meetple.backend.domain.legal.dto.response;

import com.meetple.backend.domain.legal.entity.LegalDocument;
import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import java.time.LocalDateTime;

public record SignupLegalDocumentResponse(
        LegalDocumentType type,
        String version,
        String title,
        String content,
        LocalDateTime effectiveAt
) {

    public static SignupLegalDocumentResponse from(LegalDocument document) {
        return new SignupLegalDocumentResponse(
                document.getType(),
                document.getVersion(),
                document.getTitle(),
                document.getContent(),
                document.getEffectiveAt()
        );
    }
}
