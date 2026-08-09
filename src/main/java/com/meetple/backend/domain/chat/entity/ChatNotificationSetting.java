package com.meetple.backend.domain.chat.entity;

import com.meetple.backend.domain.meeting.entity.Meeting;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(
        name = "chat_notification_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_notification_settings_meeting_member",
                columnNames = {"meeting_id", "member_id"}
        ),
        indexes = @Index(
                name = "idx_chat_notification_settings_member",
                columnList = "member_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatNotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Member member;

    @Column(nullable = false)
    private boolean enabled;

    private ChatNotificationSetting(Meeting meeting, Member member, boolean enabled) {
        this.meeting = meeting;
        this.member = member;
        this.enabled = enabled;
    }

    public static ChatNotificationSetting create(
            Meeting meeting,
            Member member,
            boolean enabled
    ) {
        return new ChatNotificationSetting(meeting, member, enabled);
    }

    public void update(boolean enabled) {
        this.enabled = enabled;
    }
}
