package com.meetple.backend.domain.moderation.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "moderation_actions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "administrator_member_id", nullable = false)
    private Long administratorMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private AdminModerationActionType actionType;

    @Column(name = "target_member_id")
    private Long targetMemberId;

    @Column(name = "target_meeting_id")
    private Long targetMeetingId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ModerationAction(
            Long reportId,
            Long administratorMemberId,
            AdminModerationActionType actionType,
            Long targetMemberId,
            Long targetMeetingId,
            String reason,
            LocalDateTime effectiveUntil
    ) {
        this.reportId = reportId;
        this.administratorMemberId = administratorMemberId;
        this.actionType = actionType;
        this.targetMemberId = targetMemberId;
        this.targetMeetingId = targetMeetingId;
        this.reason = reason;
        this.effectiveUntil = effectiveUntil;
    }

    public static ModerationAction create(
            Long reportId,
            Long administratorMemberId,
            AdminModerationActionType actionType,
            Long targetMemberId,
            Long targetMeetingId,
            String reason,
            LocalDateTime effectiveUntil
    ) {
        return new ModerationAction(
                reportId,
                administratorMemberId,
                actionType,
                targetMemberId,
                targetMeetingId,
                reason,
                effectiveUntil
        );
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
