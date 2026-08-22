package com.meetple.backend.domain.auth.dto.request;

import com.meetple.backend.domain.legal.entity.LegalDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupLegalDocumentRequest(
        @NotNull(message = "약관 유형이 필요합니다.")
        LegalDocumentType type,

        @NotBlank(message = "약관 버전이 필요합니다.")
        @Size(max = 50, message = "약관 버전은 50자 이하여야 합니다.")
        String version
) {
}
