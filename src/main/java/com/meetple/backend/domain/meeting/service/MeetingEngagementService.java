package com.meetple.backend.domain.meeting.service;

import com.meetple.backend.domain.meeting.dto.response.MeetingEngagementResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingMemberResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingParticipationResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingBookmark;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingEngagementService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipationRepository participationRepository;
    private final MeetingBookmarkRepository bookmarkRepository;
    private final MemberRepository memberRepository;

    public MeetingEngagementResponse getEngagement(Long memberId, Long meetingId) {
        Meeting meeting = getMeeting(meetingId);
        boolean host = meeting.isHostedBy(memberId);
        MeetingParticipationResponse participation = participationRepository
                .findByMeetingIdAndMemberId(meetingId, memberId)
                .map(MeetingParticipationResponse::from)
                .orElse(null);

        List<MeetingMemberResponse> members = new ArrayList<>();
        members.add(MeetingMemberResponse.host(meeting.getHost()));
        participationRepository.findByMeetingIdAndStatus(meetingId, ParticipationStatus.APPROVED)
                .stream()
                .map(item -> MeetingMemberResponse.participant(item.getMember()))
                .forEach(members::add);

        return new MeetingEngagementResponse(
                host,
                !host && bookmarkRepository.existsByMeetingIdAndMemberId(meetingId, memberId),
                participation,
                members
        );
    }

    @Transactional
    public void addBookmark(Long memberId, Long meetingId) {
        Meeting meeting = getMeeting(meetingId);
        if (meeting.isHostedBy(memberId)) {
            throw new BadRequestException("Host cannot bookmark own meeting.");
        }
        if (bookmarkRepository.existsByMeetingIdAndMemberId(meetingId, memberId)) {
            return;
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found."));
        try {
            bookmarkRepository.saveAndFlush(MeetingBookmark.create(meeting, member));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Meeting bookmark already exists.");
        }
    }

    @Transactional
    public void removeBookmark(Long memberId, Long meetingId) {
        bookmarkRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .ifPresent(bookmarkRepository::delete);
    }

    public PageResponse<MeetingResponse> getMyBookmarks(Long memberId, Pageable pageable) {
        return PageResponse.from(
                bookmarkRepository.findByMemberId(memberId, pageable)
                        .map(bookmark -> MeetingResponse.from(bookmark.getMeeting()))
        );
    }

    private Meeting getMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting not found."));
    }
}
