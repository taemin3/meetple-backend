package com.meetple.backend.domain.image.service;

import com.meetple.backend.domain.image.config.ImageStorageProperties;
import com.meetple.backend.domain.image.dto.request.ImageUploadUrlRequest;
import com.meetple.backend.domain.image.dto.request.ImageUploadUrlsRequest;
import com.meetple.backend.domain.image.dto.response.ImageUploadUrlResponse;
import com.meetple.backend.domain.image.entity.ImageUploadPurpose;
import com.meetple.backend.domain.image.storage.ImageStorageClient;
import com.meetple.backend.domain.image.storage.ImageUploadObject;
import com.meetple.backend.domain.image.storage.PresignedImageUpload;
import com.meetple.backend.global.exception.BadRequestException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final ImageStorageClient imageStorageClient;
    private final ImageStorageProperties properties;

    public List<ImageUploadUrlResponse> createUploadUrls(Long memberId, ImageUploadUrlsRequest request) {
        return request.images()
                .stream()
                .map(image -> createUploadUrl(memberId, image))
                .toList();
    }

    public ImageUploadUrlResponse createUploadUrl(Long memberId, ImageUploadUrlRequest request) {
        String contentType = normalizeContentType(request.contentType());
        validateContentType(contentType);
        validateContentLength(request.contentLength());

        String objectKey = createObjectKey(
                memberId,
                request.purpose(),
                request.fileName(),
                contentType
        );
        PresignedImageUpload upload = imageStorageClient.createPresignedUpload(new ImageUploadObject(
                objectKey,
                contentType,
                request.contentLength(),
                properties.uploadUrlExpiration()
        ));

        return new ImageUploadUrlResponse(
                upload.uploadUrl(),
                upload.fileUrl(),
                upload.objectKey(),
                upload.method(),
                upload.headers(),
                upload.expiresIn().toSeconds()
        );
    }

    private String normalizeContentType(String contentType) {
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private void validateContentType(String contentType) {
        if (!properties.allowedContentTypes().contains(contentType)) {
            throw new BadRequestException("지원하지 않는 이미지 형식입니다.");
        }
    }

    private void validateContentLength(long contentLength) {
        if (contentLength > properties.maxSizeBytes()) {
            throw new BadRequestException("이미지 파일 크기가 너무 큽니다.");
        }
    }

    private String createObjectKey(
            Long memberId,
            ImageUploadPurpose purpose,
            String fileName,
            String contentType
    ) {
        String extension = resolveExtension(fileName, contentType);
        return String.join("/",
                sanitizePathSegment(properties.keyPrefix()),
                purpose.pathSegment(),
                memberId.toString(),
                UUID.randomUUID() + "." + extension
        );
    }

    private String resolveExtension(String fileName, String contentType) {
        String fileExtension = extensionFromFileName(fileName);
        String contentTypeExtension = CONTENT_TYPE_EXTENSIONS.get(contentType);
        if (StringUtils.hasText(fileExtension) && fileExtension.equals(contentTypeExtension)) {
            return fileExtension;
        }
        return contentTypeExtension;
    }

    private String extensionFromFileName(String fileName) {
        String normalized = fileName.replace("\\", "/");
        String nameOnly = normalized.substring(normalized.lastIndexOf('/') + 1);
        int extensionStart = nameOnly.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == nameOnly.length() - 1) {
            return "";
        }
        return nameOnly.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizePathSegment(String value) {
        String sanitized = value.replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "")
                .replaceAll("[^a-zA-Z0-9/_-]", "");
        if (!StringUtils.hasText(sanitized)) {
            return "images";
        }
        return sanitized;
    }
}
