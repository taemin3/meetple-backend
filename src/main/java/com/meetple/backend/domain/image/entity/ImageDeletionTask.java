package com.meetple.backend.domain.image.entity;

import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "image_deletion_tasks",
        indexes = @Index(
                name = "idx_image_deletion_tasks_next_attempt_at",
                columnList = "next_attempt_at, id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageDeletionTask extends BaseTimeEntity {

    private static final int LAST_ERROR_MAX_LENGTH = 500;
    private static final long MAX_RETRY_DELAY_SECONDS = 86_400;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    private ImageDeletionTask(String objectKey, LocalDateTime nextAttemptAt) {
        this.objectKey = objectKey;
        this.attempts = 0;
        this.nextAttemptAt = nextAttemptAt;
    }

    public static ImageDeletionTask create(String objectKey, LocalDateTime now) {
        return new ImageDeletionTask(objectKey, now);
    }

    public void markFailed(String errorMessage, LocalDateTime now) {
        long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS, 60L << Math.min(attempts, 20));
        this.attempts++;
        this.nextAttemptAt = now.plusSeconds(delaySeconds);
        this.lastError = truncate(errorMessage);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= LAST_ERROR_MAX_LENGTH
                ? value
                : value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
