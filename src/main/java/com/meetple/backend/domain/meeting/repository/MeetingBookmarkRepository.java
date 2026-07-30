package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.MeetingBookmark;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingBookmarkRepository extends JpaRepository<MeetingBookmark, Long> {

    boolean existsByMeetingIdAndMemberId(Long meetingId, Long memberId);

    Optional<MeetingBookmark> findByMeetingIdAndMemberId(Long meetingId, Long memberId);

    void deleteByMeetingId(Long meetingId);

    @EntityGraph(attributePaths = {"meeting", "meeting.host", "meeting.category"})
    Page<MeetingBookmark> findByMemberId(Long memberId, Pageable pageable);

    long countByMemberId(Long memberId);
}
