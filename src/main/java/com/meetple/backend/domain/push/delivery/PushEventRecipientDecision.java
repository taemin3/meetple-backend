package com.meetple.backend.domain.push.delivery;

import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "push_event_recipient_decisions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_event_recipient_decisions_event_member",
                columnNames = {"event_id", "member_id"}
        ),
        indexes = @Index(
                name = "idx_push_event_recipient_decisions_event",
                columnList = "event_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushEventRecipientDecision extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private boolean suppressed;

    private PushEventRecipientDecision(UUID eventId, Long memberId, boolean suppressed) {
        this.eventId = eventId;
        this.memberId = memberId;
        this.suppressed = suppressed;
    }

    public static PushEventRecipientDecision create(
            UUID eventId,
            Long memberId,
            boolean suppressed
    ) {
        return new PushEventRecipientDecision(eventId, memberId, suppressed);
    }
}
