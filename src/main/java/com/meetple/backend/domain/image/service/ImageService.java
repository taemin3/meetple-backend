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
import java.util.regex.Pattern;
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
    private static final Pattern GENERATED_IMAGE_FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$"
    );
    private static final String UNTRUSTED_IMAGE_PATH_MESSAGE = "신뢰할 수 없는 이미지 경로입니다.";

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

    public String resolveOwnedFileUrl(Long memberId, ImageUploadPurpose purpose, String candidateUrl) {
        return createFileUrl(resolveOwnedObjectKeyFromFileUrl(memberId, purpose, candidateUrl));
    }

    public String resolveOwnedObjectKeyFromFileUrl(
            Long memberId,
            ImageUploadPurpose purpose,
            String candidateUrl
    ) {
        if (!StringUtils.hasText(candidateUrl)) {
            throw new BadRequestException(UNTRUSTED_IMAGE_PATH_MESSAGE);
        }

        String ownerObjectKeyPrefix = ownerObjectKeyPrefix(memberId, purpose);
        String ownerFileUrlPrefix = imageStorageClient.createFileUrl(ownerObjectKeyPrefix) + "/";
        String normalizedCandidate = candidateUrl.trim();
        if (!normalizedCandidate.startsWith(ownerFileUrlPrefix)) {
            throw new BadRequestException(UNTRUSTED_IMAGE_PATH_MESSAGE);
        }
        return validateGeneratedObjectKey(
                ownerObjectKeyPrefix,
                normalizedCandidate.substring(ownerFileUrlPrefix.length())
        );
    }

    public String resolveOwnedObjectKey(
            Long memberId,
            ImageUploadPurpose purpose,
            String candidateObjectKey
    ) {
        if (!StringUtils.hasText(candidateObjectKey)) {
            throw new BadRequestException(UNTRUSTED_IMAGE_PATH_MESSAGE);
        }

        String ownerObjectKeyPrefix = ownerObjectKeyPrefix(memberId, purpose);
        String expectedPrefix = ownerObjectKeyPrefix + "/";
        String normalizedCandidate = candidateObjectKey.trim().replace("\\", "/");
        if (!normalizedCandidate.startsWith(expectedPrefix)) {
            throw new BadRequestException(UNTRUSTED_IMAGE_PATH_MESSAGE);
        }
        return validateGeneratedObjectKey(
                ownerObjectKeyPrefix,
                normalizedCandidate.substring(expectedPrefix.length())
        );
    }

    public String createFileUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        return imageStorageClient.createFileUrl(objectKey.trim());
    }

    private String ownerObjectKeyPrefix(Long memberId, ImageUploadPurpose purpose) {
        return String.join("/",
                sanitizePathSegment(properties.keyPrefix()),
                purpose.pathSegment(),
                memberId.toString()
        );
    }

    private String validateGeneratedObjectKey(String ownerObjectKeyPrefix, String fileName) {
        if (!GENERATED_IMAGE_FILE_NAME.matcher(fileName).matches()) {
            throw new BadRequestException(UNTRUSTED_IMAGE_PATH_MESSAGE);
        }
        return ownerObjectKeyPrefix + "/" + fileName;
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
