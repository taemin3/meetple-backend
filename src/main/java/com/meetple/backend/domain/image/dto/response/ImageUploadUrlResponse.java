package com.meetple.backend.domain.image.dto.response;

import java.util.Map;

public record ImageUploadUrlResponse(
        String uploadUrl,
        String fileUrl,
        String objectKey,
        String method,
        Map<String, String> headers,
        long expiresInSeconds
) {
}
