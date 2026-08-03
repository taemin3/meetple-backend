package com.meetple.backend.domain.chat.repository;

import com.meetple.backend.domain.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @EntityGraph(attributePaths = "sender")
    Optional<ChatMessage> findByMeetingIdAndSenderIdAndClientMessageId(
            Long meetingId,
            Long senderId,
            UUID clientMessageId
    );

    @EntityGraph(attributePaths = "sender")
    Optional<ChatMessage> findTopByMeetingIdOrderByRoomSequenceDesc(Long meetingId);

    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByMeetingIdOrderByRoomSequenceDesc(Long meetingId, Pageable pageable);

    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByMeetingIdAndRoomSequenceLessThanOrderByRoomSequenceDesc(
            Long meetingId,
            Long roomSequence,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByMeetingIdAndRoomSequenceGreaterThanOrderByRoomSequenceAsc(
            Long meetingId,
            Long roomSequence,
            Pageable pageable
    );

    long countByMeetingIdAndRoomSequenceGreaterThan(Long meetingId, Long roomSequence);
}
