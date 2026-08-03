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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "chat_messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_messages_room_sequence",
                        columnNames = {"meeting_id", "room_sequence"}
                ),
                @UniqueConstraint(
                        name = "uk_chat_messages_client_message",
                        columnNames = {"meeting_id", "sender_id", "client_message_id"}
                )
        },
        indexes = @Index(
                name = "idx_chat_messages_meeting_sequence",
                columnList = "meeting_id, room_sequence"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    @Column(name = "room_sequence", nullable = false)
    private Long roomSequence;

    @Column(name = "client_message_id", nullable = false, columnDefinition = "uuid")
    private UUID clientMessageId;

    @Column(nullable = false, length = 1000)
    private String content;

    private ChatMessage(
            Meeting meeting,
            Member sender,
            Long roomSequence,
            UUID clientMessageId,
            String content
    ) {
        this.meeting = meeting;
        this.sender = sender;
        this.roomSequence = roomSequence;
        this.clientMessageId = clientMessageId;
        this.content = content;
    }

    public static ChatMessage create(
            Meeting meeting,
            Member sender,
            Long roomSequence,
            UUID clientMessageId,
            String content
    ) {
        return new ChatMessage(meeting, sender, roomSequence, clientMessageId, content.trim());
    }
}
