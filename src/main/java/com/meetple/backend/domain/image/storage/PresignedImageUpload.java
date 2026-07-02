package com.meetple.backend.domain.image.storage;

import java.time.Duration;
import java.util.Map;

public record PresignedImageUpload(
        String uploadUrl,
        String fileUrl,
        String objectKey,
        String method,
        Map<String, String> headers,
        Duration expiresIn
) {
}
