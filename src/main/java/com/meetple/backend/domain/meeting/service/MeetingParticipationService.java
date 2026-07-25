package com.meetple.backend.domain.meeting.service;

import com.meetple.backend.domain.meeting.dto.request.CreateMeetingParticipationRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingParticipationResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingParticipationService {

    private static final String MEMBER_NOT_FOUND_MESSAGE = "Member not found.";
    private static final String MEETING_NOT_FOUND_MESSAGE = "Meeting not found.";
    private static final String PARTICIPATION_NOT_FOUND_MESSAGE = "Participation not found.";
    private static final String HOST_CANNOT_APPLY_MESSAGE = "Host cannot apply to own meeting.";
    private static final String DUPLICATE_PARTICIPATION_MESSAGE = "Participation already exists.";
    private static final String MEETING_NOT_RECRUITING_MESSAGE = "Meeting is not recruiting.";
    private static final String MEETING_FULL_MESSAGE = "Meeting capacity is full.";
    private static final String HOST_ONLY_MESSAGE = "Only meeting host can manage participation requests.";
    private static final String APPLICANT_ONLY_MESSAGE = "Only applicant can cancel participation.";
    private static final String PENDING_ONLY_MESSAGE = "Only pending participation can be reviewed.";
    private static final String ACTIVE_ONLY_MESSAGE = "Only pending or approved participation can be canceled.";
    private static final String INVALID_STATUS_MESSAGE = "Unsupported participation status.";
    private static final String INVALID_SORT_PROPERTY_MESSAGE = "Unsupported sort property.";
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "status",
            "createdAt",
            "updatedAt",
            "reviewedAt",
            "canceledAt"
    );

    private final MeetingRepository meetingRepository;
    private final MeetingParticipationRepository participationRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional
    public MeetingParticipationResponse applyParticipation(
            Long memberId,
            Long meetingId,
            CreateMeetingParticipationRequest request
    ) {
        Meeting meeting = getMeeting(meetingId);
        Member member = getMember(memberId);
        ensureApplicantCanApply(meeting, memberId);

        var existing = participationRepository.findByMeetingIdAndMemberIdForUpdate(meetingId, memberId);
        if (existing.isPresent()) {
            MeetingParticipation participation = existing.get();
            if (participation.getStatus() == ParticipationStatus.CANCELED) {
                participation.reapply(normalizeMessage(request == null ? null : request.message()));
                notificationService.notify(
                        meeting.getHost(),
                        "PARTICIPATION_APPLIED",
                        "새 참여 신청",
                        member.getNickname() + "님이 " + meeting.getTitle() + " 모임에 참여를 신청했습니다.",
                        meetingId
                );
                return MeetingParticipationResponse.from(participation);
            }
            throw new ConflictException(DUPLICATE_PARTICIPATION_MESSAGE);
        }

        MeetingParticipation participation = MeetingParticipation.apply(
                meeting,
                member,
                normalizeMessage(request == null ? null : request.message())
        );

        try {
            MeetingParticipation saved = participationRepository.saveAndFlush(participation);
            notificationService.notify(
                    meeting.getHost(),
                    "PARTICIPATION_APPLIED",
                    "새 참여 신청",
                    member.getNickname() + "님이 " + meeting.getTitle() + " 모임에 참여를 신청했습니다.",
                    meetingId
            );
            return MeetingParticipationResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICATE_PARTICIPATION_MESSAGE);
        }
    }

    public PageResponse<MeetingParticipationResponse> getMeetingParticipations(
            Long hostId,
            Long meetingId,
            String status,
            Pageable pageable
    ) {
        validateSort(pageable);
        Meeting meeting = getMeeting(meetingId);
        ensureHost(meeting, hostId);

        ParticipationStatus participationStatus = parseStatus(status);
        Page<MeetingParticipationResponse> participations = (participationStatus == null
                ? participationRepository.findByMeetingId(meetingId, pageable)
                : participationRepository.findByMeetingIdAndStatus(meetingId, participationStatus, pageable))
                .map(MeetingParticipationResponse::from);
        return PageResponse.from(participations);
    }

    @Transactional
    public MeetingParticipationResponse approveParticipation(Long hostId, Long meetingId, Long participationId) {
        Meeting meeting = getMeetingForUpdate(meetingId);
        MeetingParticipation participation = getParticipationForUpdate(meetingId, participationId);
        ensureHost(meeting, hostId);
        ensurePending(participation);
        ensureMeetingCanAccept(meeting);

        participation.approve();
        meeting.increaseCurrentPeople();
        notificationService.notify(
                participation.getMember(),
                "PARTICIPATION_APPROVED",
                "참여 승인",
                meeting.getTitle() + " 모임 참여가 승인되었습니다.",
                meetingId
        );
        return MeetingParticipationResponse.from(participation);
    }

    @Transactional
    public MeetingParticipationResponse rejectParticipation(Long hostId, Long meetingId, Long participationId) {
        Meeting meeting = getMeetingForUpdate(meetingId);
        MeetingParticipation participation = getParticipationForUpdate(meetingId, participationId);
        ensureHost(meeting, hostId);
        ensurePending(participation);

        participation.reject();
        notificationService.notify(
                participation.getMember(),
                "PARTICIPATION_REJECTED",
                "참여 거절",
                meeting.getTitle() + " 모임 참여 신청이 거절되었습니다.",
                meetingId
        );
        return MeetingParticipationResponse.from(participation);
    }

    @Transactional
    public MeetingParticipationResponse cancelParticipation(Long memberId, Long meetingId, Long participationId) {
        Meeting meeting = getMeetingForUpdate(meetingId);
        MeetingParticipation participation = getParticipationForUpdate(meetingId, participationId);
        ensureApplicant(participation, memberId);
        ensureCancelable(participation);
        if (!LocalDateTime.now().isBefore(meeting.getMeetingDate())) {
            throw new BadRequestException("Participation cannot be canceled after the meeting starts.");
        }

        if (participation.getStatus() == ParticipationStatus.APPROVED) {
            meeting.decreaseCurrentPeople();
        }
        participation.cancel();
        notificationService.notify(
                meeting.getHost(),
                "PARTICIPATION_CANCELED",
                "참여 취소",
                participation.getMember().getNickname() + "님이 " + meeting.getTitle() + " 참여를 취소했습니다.",
                meetingId
        );
        return MeetingParticipationResponse.from(participation);
    }

    private Meeting getMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException(MEETING_NOT_FOUND_MESSAGE));
    }

    private Meeting getMeetingForUpdate(Long meetingId) {
        return meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new NotFoundException(MEETING_NOT_FOUND_MESSAGE));
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
    }

    private MeetingParticipation getParticipationForUpdate(Long meetingId, Long participationId) {
        return participationRepository.findByIdAndMeetingIdForUpdate(participationId, meetingId)
                .orElseThrow(() -> new NotFoundException(PARTICIPATION_NOT_FOUND_MESSAGE));
    }

    private void ensureApplicantCanApply(Meeting meeting, Long memberId) {
        if (meeting.isHostedBy(memberId)) {
            throw new BadRequestException(HOST_CANNOT_APPLY_MESSAGE);
        }
        if (isFull(meeting)) {
            throw new BadRequestException(MEETING_FULL_MESSAGE);
        }
        if (meeting.getStatus() != MeetingStatus.RECRUITING) {
            throw new BadRequestException(MEETING_NOT_RECRUITING_MESSAGE);
        }
    }

    private void ensureHost(Meeting meeting, Long memberId) {
        if (!meeting.isHostedBy(memberId)) {
            throw new ForbiddenException(HOST_ONLY_MESSAGE);
        }
    }

    private void ensureApplicant(MeetingParticipation participation, Long memberId) {
        if (!participation.getMember().getId().equals(memberId)) {
            throw new ForbiddenException(APPLICANT_ONLY_MESSAGE);
        }
    }

    private void ensurePending(MeetingParticipation participation) {
        if (participation.getStatus() != ParticipationStatus.PENDING) {
            throw new BadRequestException(PENDING_ONLY_MESSAGE);
        }
    }

    private void ensureCancelable(MeetingParticipation participation) {
        if (participation.getStatus() != ParticipationStatus.PENDING
                && participation.getStatus() != ParticipationStatus.APPROVED) {
            throw new BadRequestException(ACTIVE_ONLY_MESSAGE);
        }
    }

    private void ensureMeetingCanAccept(Meeting meeting) {
        if (isFull(meeting)) {
            throw new BadRequestException(MEETING_FULL_MESSAGE);
        }
        if (meeting.getStatus() != MeetingStatus.RECRUITING) {
            throw new BadRequestException(MEETING_NOT_RECRUITING_MESSAGE);
        }
    }

    private boolean isFull(Meeting meeting) {
        return meeting.getStatus() == MeetingStatus.FULL
                || meeting.getCurrentPeople() >= meeting.getMaxPeople();
    }

    private ParticipationStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }

        try {
            return ParticipationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_STATUS_MESSAGE);
        }
    }

    private void validateSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException(INVALID_SORT_PROPERTY_MESSAGE);
            }
        }
    }

    private String normalizeMessage(String message) {
        return StringUtils.hasText(message) ? message.trim() : null;
    }
}
