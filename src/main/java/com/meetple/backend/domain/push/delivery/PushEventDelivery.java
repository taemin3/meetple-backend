package com.meetple.backend.domain.push.delivery;

import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "push_event_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_event_deliveries_event_device",
                columnNames = {"event_id", "device_token_id"}
        ),
        indexes = @Index(
                name = "idx_push_event_deliveries_status_updated_at",
                columnList = "status, updated_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushEventDelivery extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "device_token_id", nullable = false)
    private Long deviceTokenId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PushDeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    private PushEventDelivery(UUID eventId, Long deviceTokenId) {
        this.eventId = eventId;
        this.deviceTokenId = deviceTokenId;
        this.status = PushDeliveryStatus.PENDING;
    }

    public static PushEventDelivery create(UUID eventId, Long deviceTokenId) {
        return new PushEventDelivery(eventId, deviceTokenId);
    }

    public boolean isTerminal() {
        return status == PushDeliveryStatus.SENT || status == PushDeliveryStatus.INVALID_TOKEN;
    }

    public void startAttempt() {
        this.status = PushDeliveryStatus.PENDING;
        this.attempts++;
        this.lastErrorCode = null;
    }

    public void markSent() {
        this.status = PushDeliveryStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.lastErrorCode = null;
    }

    public void markInvalidToken() {
        this.status = PushDeliveryStatus.INVALID_TOKEN;
        this.lastErrorCode = "UNREGISTERED";
    }

    public void markFailed(String errorCode) {
        this.status = PushDeliveryStatus.FAILED;
        this.lastErrorCode = errorCode;
    }
}
