package com.meetple.backend.domain.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AccountDeletionCompleteRequest(
        @NotBlank @Email String email,
        @NotBlank String accountDeletionToken,
        @AssertTrue(message = "계정 삭제에 최종 동의해야 합니다.") boolean confirmed
) {
}
