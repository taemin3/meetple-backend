package com.meetple.backend.domain.chat.repository;

import com.meetple.backend.domain.chat.entity.ChatRoomSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomSequenceRepository extends JpaRepository<ChatRoomSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sequence from ChatRoomSequence sequence where sequence.meetingId = :meetingId")
    Optional<ChatRoomSequence> findByMeetingIdForUpdate(@Param("meetingId") Long meetingId);
}
