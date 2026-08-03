package com.meetple.backend.domain.chat.repository;

import com.meetple.backend.domain.chat.entity.ChatReadState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, Long> {

    Optional<ChatReadState> findByMeetingIdAndMemberId(Long meetingId, Long memberId);
}
