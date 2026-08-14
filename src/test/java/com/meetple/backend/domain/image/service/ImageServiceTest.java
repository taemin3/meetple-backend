package com.meetple.backend.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.image.config.ImageStorageProperties;
import com.meetple.backend.domain.image.dto.request.ImageUploadUrlRequest;
import com.meetple.backend.domain.image.dto.request.ImageUploadUrlsRequest;
import com.meetple.backend.domain.image.dto.response.ImageUploadUrlResponse;
import com.meetple.backend.domain.image.entity.ImageUploadPurpose;
import com.meetple.backend.domain.image.storage.ImageStorageClient;
import com.meetple.backend.domain.image.storage.ImageUploadObject;
import com.meetple.backend.domain.image.storage.PresignedImageUpload;
import com.meetple.backend.global.exception.BadRequestException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ImageServiceTest {

    private final CapturingImageStorageClient imageStorageClient = new CapturingImageStorageClient();
    private final ImageStorageProperties properties = new ImageStorageProperties(
            "meetple-images",
            "ap-northeast-2",
            "",
            "https://cdn.meetple.com",
            "access-key",
            "secret-key",
            "images",
            Duration.ofMinutes(3),
            1024L,
            false,
            List.of("image/jpeg", "image/png", "image/webp")
    );
    private final ImageService imageService = new ImageService(imageStorageClient, properties);

    @Test
    void createUploadUrlReturnsPresignedUploadContract() {
        ImageUploadUrlRequest request = new ImageUploadUrlRequest(
                ImageUploadPurpose.PROFILE,
                "avatar.PNG",
                "IMAGE/PNG",
                512L
        );

        ImageUploadUrlResponse response = imageService.createUploadUrl(7L, request);

        assertThat(imageStorageClient.uploadObject.objectKey())
                .startsWith("images/profile/7/")
                .endsWith(".png");
        assertThat(imageStorageClient.uploadObject.contentType()).isEqualTo("image/png");
        assertThat(imageStorageClient.uploadObject.contentLength()).isEqualTo(512L);
        assertThat(imageStorageClient.uploadObject.expiresIn()).isEqualTo(Duration.ofMinutes(3));
        assertThat(response.uploadUrl()).isEqualTo("https://upload.meetple.com/" + response.objectKey());
        assertThat(response.fileUrl()).isEqualTo("https://cdn.meetple.com/" + response.objectKey());
        assertThat(response.method()).isEqualTo("PUT");
        assertThat(response.headers()).containsEntry("Content-Type", "image/png");
        assertThat(response.expiresInSeconds()).isEqualTo(180L);
    }

    @Test
    void createUploadUrlUsesContentTypeExtensionWhenFileExtensionIsDifferent() {
        ImageUploadUrlRequest request = new ImageUploadUrlRequest(
                ImageUploadPurpose.MEETING,
                "thumbnail.jpg",
                "image/webp",
                512L
        );

        ImageUploadUrlResponse response = imageService.createUploadUrl(7L, request);

        assertThat(response.objectKey())
                .startsWith("images/meeting/7/")
                .endsWith(".webp");
    }

    @Test
    void createUploadUrlsReturnsPresignedUploadContracts() {
        ImageUploadUrlsRequest request = new ImageUploadUrlsRequest(List.of(
                new ImageUploadUrlRequest(ImageUploadPurpose.MEETING, "first.png", "image/png", 512L),
                new ImageUploadUrlRequest(ImageUploadPurpose.MEETING, "second.webp", "image/webp", 256L)
        ));

        List<ImageUploadUrlResponse> responses = imageService.createUploadUrls(7L, request);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(ImageUploadUrlResponse::objectKey)
                .allSatisfy(objectKey -> assertThat(objectKey).startsWith("images/meeting/7/"));
        assertThat(imageStorageClient.uploadObjects)
                .extracting(ImageUploadObject::contentType)
                .containsExactly("image/png", "image/webp");
    }

    @Test
    void createUploadUrlRejectsUnsupportedContentType() {
        ImageUploadUrlRequest request = new ImageUploadUrlRequest(
                ImageUploadPurpose.PROFILE,
                "avatar.gif",
                "image/gif",
                512L
        );

        assertThatThrownBy(() -> imageService.createUploadUrl(7L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("지원하지 않는 이미지 형식입니다.");
    }

    @Test
    void createUploadUrlRejectsTooLargeImage() {
        ImageUploadUrlRequest request = new ImageUploadUrlRequest(
                ImageUploadPurpose.PROFILE,
                "avatar.png",
                "image/png",
                2048L
        );

        assertThatThrownBy(() -> imageService.createUploadUrl(7L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("이미지 파일 크기가 너무 큽니다.");
    }

    @Test
    void resolveOwnedFileUrlAcceptsGeneratedUrlForCurrentMemberAndPurpose() {
        String fileUrl = " https://cdn.meetple.com/images/profile/7/"
                + "550e8400-e29b-41d4-a716-446655440000.png ";

        String resolved = imageService.resolveOwnedFileUrl(7L, ImageUploadPurpose.PROFILE, fileUrl);

        assertThat(resolved).isEqualTo(
                "https://cdn.meetple.com/images/profile/7/550e8400-e29b-41d4-a716-446655440000.png"
        );
    }

    @Test
    void resolveOwnedFileUrlRejectsExternalUrl() {
        assertThatThrownBy(() -> imageService.resolveOwnedFileUrl(
                7L,
                ImageUploadPurpose.PROFILE,
                "https://tracker.example/avatar.png"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("신뢰할 수 없는 이미지 경로입니다.");
    }

    @Test
    void resolveOwnedFileUrlRejectsOtherMemberOrPurpose() {
        assertThatThrownBy(() -> imageService.resolveOwnedFileUrl(
                7L,
                ImageUploadPurpose.PROFILE,
                "https://cdn.meetple.com/images/profile/8/550e8400-e29b-41d4-a716-446655440000.png"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("신뢰할 수 없는 이미지 경로입니다.");

        assertThatThrownBy(() -> imageService.resolveOwnedFileUrl(
                7L,
                ImageUploadPurpose.PROFILE,
                "https://cdn.meetple.com/images/meeting/7/550e8400-e29b-41d4-a716-446655440000.png"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("신뢰할 수 없는 이미지 경로입니다.");
    }

    @Test
    void resolveOwnedFileUrlRejectsFileNameNotGeneratedByUploadApi() {
        assertThatThrownBy(() -> imageService.resolveOwnedFileUrl(
                7L,
                ImageUploadPurpose.PROFILE,
                "https://cdn.meetple.com/images/profile/7/avatar.png"
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("신뢰할 수 없는 이미지 경로입니다.");
    }

    private static class CapturingImageStorageClient implements ImageStorageClient {

        private ImageUploadObject uploadObject;
        private final List<ImageUploadObject> uploadObjects = new ArrayList<>();

        @Override
        public PresignedImageUpload createPresignedUpload(ImageUploadObject uploadObject) {
            this.uploadObject = uploadObject;
            this.uploadObjects.add(uploadObject);
            return new PresignedImageUpload(
                    "https://upload.meetple.com/" + uploadObject.objectKey(),
                    "https://cdn.meetple.com/" + uploadObject.objectKey(),
                    uploadObject.objectKey(),
                    "PUT",
                    Map.of("Content-Type", uploadObject.contentType()),
                    uploadObject.expiresIn()
            );
        }

        @Override
        public String createFileUrl(String objectKey) {
            return "https://cdn.meetple.com/" + objectKey;
        }
    }
}
