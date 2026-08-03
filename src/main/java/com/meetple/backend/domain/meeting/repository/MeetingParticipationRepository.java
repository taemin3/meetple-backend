package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingParticipationRepository extends JpaRepository<MeetingParticipation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"meeting", "member"})
    @Query("""
            select p
            from MeetingParticipation p
            where p.id = :id
              and p.meeting.id = :meetingId
            """)
    Optional<MeetingParticipation> findByIdAndMeetingIdForUpdate(
            @Param("id") Long id,
            @Param("meetingId") Long meetingId
    );

    Optional<MeetingParticipation> findByMeetingIdAndMemberId(Long meetingId, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"meeting", "member"})
    @Query("""
            select p
            from MeetingParticipation p
            where p.meeting.id = :meetingId
              and p.member.id = :memberId
            """)
    Optional<MeetingParticipation> findByMeetingIdAndMemberIdForUpdate(
            @Param("meetingId") Long meetingId,
            @Param("memberId") Long memberId
    );

    boolean existsByMeetingIdAndMemberId(Long meetingId, Long memberId);

    boolean existsByMeetingIdAndMemberIdAndStatus(
            Long meetingId,
            Long memberId,
            ParticipationStatus status
    );

    boolean existsByMeetingId(Long meetingId);

    List<MeetingParticipation> findByMeetingId(Long meetingId);

    @EntityGraph(attributePaths = {"meeting", "member"})
    Page<MeetingParticipation> findByMeetingId(Long meetingId, Pageable pageable);

    @EntityGraph(attributePaths = {"meeting", "member"})
    Page<MeetingParticipation> findByMeetingIdAndStatus(
            Long meetingId,
            ParticipationStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"meeting", "meeting.host", "meeting.category", "member"})
    Page<MeetingParticipation> findByMemberId(Long memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"meeting", "meeting.host", "meeting.category", "member"})
    Page<MeetingParticipation> findByMemberIdAndStatus(
            Long memberId,
            ParticipationStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"meeting", "meeting.host", "meeting.category", "member"})
    Page<MeetingParticipation> findByMemberIdAndStatusAndMeetingStatusIn(
            Long memberId,
            ParticipationStatus status,
            List<MeetingStatus> meetingStatuses,
            Pageable pageable
    );

    long countByMemberIdAndStatusAndMeetingStatusIn(
            Long memberId,
            ParticipationStatus status,
            List<MeetingStatus> meetingStatuses
    );

    @EntityGraph(attributePaths = "member")
    List<MeetingParticipation> findByMeetingIdAndStatus(
            Long meetingId,
            ParticipationStatus status
    );
}
