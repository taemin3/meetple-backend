package com.meetple.backend.domain.meeting.entity;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "meeting_participations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_meeting_participations_meeting_member",
                        columnNames = {"meeting_id", "member_id"}
                )
        },
        indexes = {
                @Index(name = "idx_meeting_participations_meeting_id", columnList = "meeting_id"),
                @Index(name = "idx_meeting_participations_member_id", columnList = "member_id"),
                @Index(name = "idx_meeting_participations_status", columnList = "status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingParticipation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ParticipationStatus status;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private MeetingParticipation(Meeting meeting, Member member, String message) {
        this.meeting = meeting;
        this.member = member;
        this.message = message;
        this.status = ParticipationStatus.PENDING;
    }

    public static MeetingParticipation apply(Meeting meeting, Member member, String message) {
        return new MeetingParticipation(meeting, member, message);
    }

    public void approve() {
        this.status = ParticipationStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = ParticipationStatus.REJECTED;
        this.reviewedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ParticipationStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }
}
