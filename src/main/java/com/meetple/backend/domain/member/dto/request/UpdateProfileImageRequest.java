package com.meetple.backend.domain.member.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileImageRequest(
        @Size(max = 255, message = "Profile image URL must be 255 characters or fewer.")
        String profileImageUrl,

        @Size(max = 255, message = "Profile image object key must be 255 characters or fewer.")
        String profileImageObjectKey
) {
}
