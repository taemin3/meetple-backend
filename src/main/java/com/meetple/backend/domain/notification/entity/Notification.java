package com.meetple.backend.domain.notification.entity;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "notifications",
        indexes = @Index(
                name = "idx_notifications_member_created_at",
                columnList = "member_id, created_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    private Notification(Member member, String type, String title, String message, Long meetingId) {
        this.member = member;
        this.type = type;
        this.title = title;
        this.message = message;
        this.meetingId = meetingId;
    }

    public static Notification create(
            Member member,
            String type,
            String title,
            String message,
            Long meetingId
    ) {
        return new Notification(member, type, title, message, meetingId);
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
