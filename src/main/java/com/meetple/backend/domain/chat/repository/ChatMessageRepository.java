package com.meetple.backend.domain.chat.repository;

import com.meetple.backend.domain.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("""
            select message
            from ChatMessage message
            where message.meeting.id in :meetingIds
              and message.roomSequence = (
                    select max(candidate.roomSequence)
                    from ChatMessage candidate
                    where candidate.meeting.id = message.meeting.id
              )
            """)
    List<ChatMessage> findLatestByMeetingIds(@Param("meetingIds") List<Long> meetingIds);

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

    @Query(
            value = """
                    select cm.meeting_id as "meetingId", count(*) as "unreadCount"
                    from chat_messages cm
                    left join chat_read_states crs
                      on crs.meeting_id = cm.meeting_id
                     and crs.member_id = :memberId
                    where cm.meeting_id in (:meetingIds)
                      and cm.room_sequence > coalesce(crs.last_read_sequence, 0)
                    group by cm.meeting_id
                    """,
            nativeQuery = true
    )
    List<ChatUnreadCountProjection> countUnreadByMeetingIds(
            @Param("memberId") Long memberId,
            @Param("meetingIds") List<Long> meetingIds
    );
}
