package com.meetple.backend.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "chat_room_sequences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomSequence {

    @Id
    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence;

    private ChatRoomSequence(Long meetingId, Long lastSequence) {
        this.meetingId = meetingId;
        this.lastSequence = lastSequence;
    }

    public static ChatRoomSequence initialize(Long meetingId) {
        return new ChatRoomSequence(meetingId, 0L);
    }

    public long next() {
        lastSequence++;
        return lastSequence;
    }
}
