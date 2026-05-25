package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipationRepository extends JpaRepository<MeetingParticipation, Long> {

    @EntityGraph(attributePaths = {"meeting", "member"})
    Optional<MeetingParticipation> findByIdAndMeetingId(Long id, Long meetingId);

    Optional<MeetingParticipation> findByMeetingIdAndMemberId(Long meetingId, Long memberId);

    boolean existsByMeetingIdAndMemberId(Long meetingId, Long memberId);

    List<MeetingParticipation> findByMeetingId(Long meetingId);

    @EntityGraph(attributePaths = {"meeting", "member"})
    Page<MeetingParticipation> findByMeetingId(Long meetingId, Pageable pageable);

    @EntityGraph(attributePaths = {"meeting", "member"})
    Page<MeetingParticipation> findByMeetingIdAndStatus(
            Long meetingId,
            ParticipationStatus status,
            Pageable pageable
    );

    Page<MeetingParticipation> findByMemberId(Long memberId, Pageable pageable);

    Page<MeetingParticipation> findByMemberIdAndStatus(
            Long memberId,
            ParticipationStatus status,
            Pageable pageable
    );
}
