package com.meetple.backend.domain.image.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.image.config.ImageStorageProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

class S3ImageStorageClientTest {

    @Test
    void createPresignedUploadReturnsSignedHeaders() {
        S3ImageStorageClient client = createClient("test-access-key", "test-secret-key");

        PresignedImageUpload upload = client.createPresignedUpload(new ImageUploadObject(
                "images/profile/1/test.png",
                "image/png",
                123L,
                Duration.ofMinutes(5)
        ));

        assertHeader(upload.headers(), "Content-Type", "image/png");
        assertHeader(upload.headers(), "Content-Length", "123");
    }

    @Test
    void usesStaticCredentialsWhenBothValuesAreConfigured() {
        S3ImageStorageClient client = createClient("test-access-key", "test-secret-key");

        AwsCredentialsProvider provider = client.createCredentialsProvider();

        assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
        assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("test-access-key");
        assertThat(provider.resolveCredentials().secretAccessKey()).isEqualTo("test-secret-key");
    }

    @Test
    void usesDefaultCredentialsChainWhenStaticCredentialsAreAbsent() {
        S3ImageStorageClient client = createClient("", "");

        assertThat(client.createCredentialsProvider())
                .isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void createPresignedUploadRejectsPartialStaticCredentials() {
        assertPartialStaticCredentialsRejected("test-access-key", "");
        assertPartialStaticCredentialsRejected("", "test-secret-key");
    }

    private void assertPartialStaticCredentialsRejected(String accessKey, String secretKey) {
        S3ImageStorageClient client = createClient(accessKey, secretKey);

        assertThatThrownBy(() -> client.createPresignedUpload(new ImageUploadObject(
                "images/profile/1/test.png",
                "image/png",
                123L,
                Duration.ofMinutes(5)
        )))
                .hasMessageContaining("이미지 저장소 설정이 누락되었습니다.");
    }

    private S3ImageStorageClient createClient(String accessKey, String secretKey) {
        return new S3ImageStorageClient(new ImageStorageProperties(
                "meetple-images",
                "ap-northeast-2",
                "",
                "https://cdn.meetple.com",
                accessKey,
                secretKey,
                "images",
                Duration.ofMinutes(5),
                5 * 1024 * 1024L,
                false,
                List.of("image/jpeg", "image/png", "image/webp")
        ));
    }

    private void assertHeader(Map<String, String> headers, String name, String value) {
        assertThat(headers.entrySet())
                .anySatisfy(entry -> {
                    assertThat(entry.getKey()).isEqualToIgnoringCase(name);
                    assertThat(entry.getValue()).isEqualTo(value);
                });
    }
}
