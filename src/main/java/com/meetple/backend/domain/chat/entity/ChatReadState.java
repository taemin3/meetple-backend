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
        name = "chat_read_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_read_states_meeting_member",
                columnNames = {"meeting_id", "member_id"}
        ),
        indexes = @Index(name = "idx_chat_read_states_member", columnList = "member_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatReadState extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "last_read_sequence", nullable = false)
    private Long lastReadSequence;

    private ChatReadState(Meeting meeting, Member member, Long lastReadSequence) {
        this.meeting = meeting;
        this.member = member;
        this.lastReadSequence = lastReadSequence;
    }

    public static ChatReadState create(Meeting meeting, Member member, Long lastReadSequence) {
        return new ChatReadState(meeting, member, lastReadSequence);
    }

    public void markRead(Long sequence) {
        if (sequence > this.lastReadSequence) {
            this.lastReadSequence = sequence;
        }
    }
}
