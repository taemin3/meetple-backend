package com.meetple.backend.domain.image.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.image.config.ImageStorageProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class S3ImageStorageClientTest {

    @Test
    void createPresignedUploadReturnsSignedHeaders() {
        S3ImageStorageClient client = new S3ImageStorageClient(new ImageStorageProperties(
                "meetple-images",
                "ap-northeast-2",
                "",
                "https://cdn.meetple.com",
                "test-access-key",
                "test-secret-key",
                "images",
                Duration.ofMinutes(5),
                5 * 1024 * 1024L,
                false,
                List.of("image/jpeg", "image/png", "image/webp")
        ));

        PresignedImageUpload upload = client.createPresignedUpload(new ImageUploadObject(
                "images/profile/1/test.png",
                "image/png",
                123L,
                Duration.ofMinutes(5)
        ));

        assertHeader(upload.headers(), "Content-Type", "image/png");
        assertHeader(upload.headers(), "Content-Length", "123");
    }

    private void assertHeader(Map<String, String> headers, String name, String value) {
        assertThat(headers.entrySet())
                .anySatisfy(entry -> {
                    assertThat(entry.getKey()).isEqualToIgnoringCase(name);
                    assertThat(entry.getValue()).isEqualTo(value);
                });
    }
}
