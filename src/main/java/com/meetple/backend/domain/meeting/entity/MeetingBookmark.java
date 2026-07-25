package com.meetple.backend.domain.meeting.entity;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.global.entity.BaseTimeEntity;
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

@Getter
@Entity
@Table(
        name = "meeting_bookmarks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_meeting_bookmarks_meeting_member",
                columnNames = {"meeting_id", "member_id"}
        ),
        indexes = {
                @Index(name = "idx_meeting_bookmarks_member_id", columnList = "member_id"),
                @Index(name = "idx_meeting_bookmarks_meeting_id", columnList = "meeting_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingBookmark extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private MeetingBookmark(Meeting meeting, Member member) {
        this.meeting = meeting;
        this.member = member;
    }

    public static MeetingBookmark create(Meeting meeting, Member member) {
        return new MeetingBookmark(meeting, member);
    }
}
