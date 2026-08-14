package com.meetple.backend.domain.image.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "image.storage")
public record ImageStorageProperties(
        String bucket,
        String region,
        String endpoint,
        String publicBaseUrl,
        String accessKey,
        String secretKey,
        String keyPrefix,
        Duration uploadUrlExpiration,
        long maxSizeBytes,
        boolean pathStyleAccessEnabled,
        List<String> allowedContentTypes
) {

    private static final String DEFAULT_REGION = "ap-northeast-2";
    private static final String DEFAULT_KEY_PREFIX = "images";
    private static final Duration DEFAULT_UPLOAD_URL_EXPIRATION = Duration.ofMinutes(5);
    private static final long DEFAULT_MAX_SIZE_BYTES = 5 * 1024 * 1024;
    private static final List<String> DEFAULT_ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public ImageStorageProperties {
        region = StringUtils.hasText(region) ? region : DEFAULT_REGION;
        keyPrefix = normalizeKeyPrefix(keyPrefix);
        uploadUrlExpiration = uploadUrlExpiration == null
                ? DEFAULT_UPLOAD_URL_EXPIRATION
                : uploadUrlExpiration;
        maxSizeBytes = maxSizeBytes <= 0 ? DEFAULT_MAX_SIZE_BYTES : maxSizeBytes;
        allowedContentTypes = allowedContentTypes == null || allowedContentTypes.isEmpty()
                ? DEFAULT_ALLOWED_CONTENT_TYPES
                : List.copyOf(allowedContentTypes);
    }

    public static String normalizeKeyPrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_KEY_PREFIX;
        }

        String normalized = value.replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "")
                .replaceAll("[^a-zA-Z0-9/_-]", "");
        return StringUtils.hasText(normalized) ? normalized : DEFAULT_KEY_PREFIX;
    }
}
