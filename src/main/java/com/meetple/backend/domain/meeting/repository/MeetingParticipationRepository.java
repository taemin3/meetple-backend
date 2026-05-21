package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipationRepository extends JpaRepository<MeetingParticipation, Long> {

    Optional<MeetingParticipation> findByMeetingIdAndMemberId(Long meetingId, Long memberId);

    boolean existsByMeetingIdAndMemberId(Long meetingId, Long memberId);

    List<MeetingParticipation> findByMeetingId(Long meetingId);

    Page<MeetingParticipation> findByMemberId(Long memberId, Pageable pageable);

    Page<MeetingParticipation> findByMemberIdAndStatus(
            Long memberId,
            ParticipationStatus status,
            Pageable pageable
    );
}
