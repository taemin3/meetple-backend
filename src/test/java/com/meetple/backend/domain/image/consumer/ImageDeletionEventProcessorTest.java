package com.meetple.backend.domain.image.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.image.config.ImageStorageProperties;
import com.meetple.backend.domain.image.storage.ImageStorageClient;
import com.meetple.backend.domain.outbox.event.OutboxEventEnvelope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageDeletionEventProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ImageStorageClient imageStorageClient;

    private ImageDeletionEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ImageDeletionEventProcessor(
                objectMapper,
                imageStorageClient,
                new ImageStorageProperties(
                        "meetple-images",
                        "ap-northeast-2",
                        "",
                        "https://cdn.meetple.com",
                        "access-key",
                        "secret-key",
                        "images",
                        Duration.ofMinutes(5),
                        5 * 1024 * 1024,
                        false,
                        List.of("image/jpeg", "image/png", "image/webp")
                )
        );
    }

    @Test
    void processDeletesTrustedUploadedImageObject() throws Exception {
        String objectKey = "images/profile/1/old.png";

        processor.process(payload(objectKey));

        verify(imageStorageClient).deleteObject(objectKey);
    }

    @Test
    void processRejectsObjectOutsideUploadedImagePaths() throws Exception {
        String objectKey = "images/categories/exercise.png";

        assertThatThrownBy(() -> processor.process(payload(objectKey)))
                .isInstanceOf(NonRetryableImageDeletionEventException.class)
                .hasMessage("Image deletion objectKey is outside an upload path.");
        verify(imageStorageClient, never()).deleteObject(objectKey);
    }

    @Test
    void processRejectsMismatchedAggregateIdentity() throws Exception {
        String objectKey = "images/profile/1/old.png";
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                UUID.randomUUID(),
                "IMAGE_DELETE_REQUESTED",
                1,
                Instant.now().toString(),
                "image",
                "images/profile/2/other.png",
                objectMapper.valueToTree(Map.of("objectKey", objectKey))
        );

        assertThatThrownBy(() -> processor.process(objectMapper.writeValueAsString(envelope)))
                .isInstanceOf(NonRetryableImageDeletionEventException.class)
                .hasMessage("Image deletion event identity does not match objectKey.");
        verify(imageStorageClient, never()).deleteObject(objectKey);
    }

    @Test
    void processPropagatesStorageFailureForKafkaRetry() throws Exception {
        String objectKey = "images/meeting/1/old.png";
        doThrow(new IllegalStateException("S3 unavailable"))
                .when(imageStorageClient).deleteObject(objectKey);

        assertThatThrownBy(() -> processor.process(payload(objectKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("S3 unavailable");
    }

    @Test
    void processRejectsMalformedPayloadWithoutRetry() {
        assertThatThrownBy(() -> processor.process("not-json"))
                .isInstanceOf(NonRetryableImageDeletionEventException.class)
                .hasMessage("Invalid image deletion event JSON.");
    }

    @Test
    void processRejectsNullPayloadWithoutRetry() {
        assertThatThrownBy(() -> processor.process(null))
                .isInstanceOf(NonRetryableImageDeletionEventException.class)
                .hasMessage("Image deletion event payload must not be null or blank.");
    }

    @Test
    void processRejectsJsonNullWithoutRetry() {
        assertThatThrownBy(() -> processor.process("null"))
                .isInstanceOf(NonRetryableImageDeletionEventException.class)
                .hasMessage("Image deletion event payload must not be JSON null.");
    }

    @Test
    void processUsesSameNormalizedKeyPrefixAsUpload() throws Exception {
        ImageStorageProperties normalizedProperties = new ImageStorageProperties(
                "meetple-images",
                "ap-northeast-2",
                "",
                "https://cdn.meetple.com",
                "access-key",
                "secret-key",
                " /my images\\nested/ ",
                Duration.ofMinutes(5),
                5 * 1024 * 1024,
                false,
                List.of("image/jpeg", "image/png", "image/webp")
        );
        ImageDeletionEventProcessor normalizedProcessor = new ImageDeletionEventProcessor(
                objectMapper,
                imageStorageClient,
                normalizedProperties
        );
        String objectKey = "myimages/nested/profile/1/old.png";

        normalizedProcessor.process(payload(objectKey));

        verify(imageStorageClient).deleteObject(objectKey);
    }

    private String payload(String objectKey) throws Exception {
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(
                UUID.randomUUID(),
                "IMAGE_DELETE_REQUESTED",
                1,
                Instant.now().toString(),
                "image",
                objectKey,
                objectMapper.valueToTree(Map.of("objectKey", objectKey))
        );
        return objectMapper.writeValueAsString(envelope);
    }
}
