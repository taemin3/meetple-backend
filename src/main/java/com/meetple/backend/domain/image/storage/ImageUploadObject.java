package com.meetple.backend.domain.image.storage;

import java.time.Duration;

public record ImageUploadObject(
        String objectKey,
        String contentType,
        long contentLength,
        Duration expiresIn
) {
}
