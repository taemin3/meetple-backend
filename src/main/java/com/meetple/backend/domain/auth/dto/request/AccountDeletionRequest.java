package com.meetple.backend.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AccountDeletionRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String currentPassword
) {
}
