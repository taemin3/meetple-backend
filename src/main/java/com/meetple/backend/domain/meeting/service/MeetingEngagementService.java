package com.meetple.backend.domain.meeting.service;

import com.meetple.backend.domain.meeting.dto.response.MeetingEngagementResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingMemberResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingParticipationResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingBookmark;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingImageRepository;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingEngagementService {

    private static final List<MeetingStatus> ACTIVE_MEETING_STATUSES = List.of(
            MeetingStatus.RECRUITING,
            MeetingStatus.FULL
    );
    private static final String INVALID_SORT_PROPERTY_MESSAGE = "Unsupported sort property.";
    private static final Set<String> MEETING_SORT_PROPERTIES = Set.of(
            "id",
            "title",
            "meetingDate",
            "currentPeople",
            "maxPeople",
            "status",
            "updatedAt"
    );

    private final MeetingRepository meetingRepository;
    private final MeetingParticipationRepository participationRepository;
    private final MeetingBookmarkRepository bookmarkRepository;
    private final MeetingImageRepository meetingImageRepository;
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
        Page<MeetingBookmark> bookmarks = bookmarkRepository.findByMemberId(
                memberId,
                toBookmarkPageable(pageable)
        );
        Map<Long, List<String>> imageUrlsByMeetingId = getImageUrlsByMeetingIds(
                bookmarks.getContent()
                        .stream()
                        .map(bookmark -> bookmark.getMeeting().getId())
                        .toList()
        );
        return PageResponse.from(bookmarks.map(bookmark -> MeetingResponse.from(
                bookmark.getMeeting(),
                imageUrlsByMeetingId.getOrDefault(bookmark.getMeeting().getId(), List.of())
        )));
    }

    public PageResponse<MeetingResponse> getMyHostedMeetings(Long memberId, Pageable pageable) {
        validateMeetingSort(pageable);
        Page<Meeting> meetings = meetingRepository.findByHostId(memberId, pageable);
        return toMeetingPageResponse(meetings);
    }

    public PageResponse<MeetingResponse> getMyJoinedMeetings(Long memberId, Pageable pageable) {
        Page<MeetingParticipation> participations =
                participationRepository.findByMemberIdAndStatusAndMeetingStatusIn(
                        memberId,
                        ParticipationStatus.APPROVED,
                        ACTIVE_MEETING_STATUSES,
                        toJoinedMeetingPageable(pageable)
                );
        Page<Meeting> meetings = participations.map(MeetingParticipation::getMeeting);
        return toMeetingPageResponse(meetings);
    }

    public PageResponse<MeetingParticipationResponse> getMyApplications(Long memberId, Pageable pageable) {
        validateParticipationSort(pageable);
        return PageResponse.from(
                participationRepository.findByMemberId(memberId, pageable)
                        .map(MeetingParticipationResponse::from)
        );
    }

    private Meeting getMeeting(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting not found."));
    }

    private Pageable toBookmarkPageable(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return pageable;
        }

        List<Sort.Order> orders = pageable.getSort()
                .stream()
                .map(this::toBookmarkSortOrder)
                .toList();
        Sort sort = orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private Sort.Order toBookmarkSortOrder(Sort.Order order) {
        if ("createdAt".equals(order.getProperty())) {
            return order;
        }
        if (MEETING_SORT_PROPERTIES.contains(order.getProperty())) {
            return order.withProperty("meeting." + order.getProperty());
        }
        throw new BadRequestException(INVALID_SORT_PROPERTY_MESSAGE);
    }

    private PageResponse<MeetingResponse> toMeetingPageResponse(Page<Meeting> meetings) {
        Map<Long, List<String>> imageUrlsByMeetingId = getImageUrlsByMeetingIds(
                meetings.getContent().stream().map(Meeting::getId).toList()
        );
        return PageResponse.from(meetings.map(meeting -> MeetingResponse.from(
                meeting,
                imageUrlsByMeetingId.getOrDefault(meeting.getId(), List.of())
        )));
    }

    private Pageable toJoinedMeetingPageable(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return pageable;
        }
        validateMeetingSort(pageable);
        List<Sort.Order> orders = pageable.getSort()
                .stream()
                .map(order -> order.withProperty("meeting." + order.getProperty()))
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private void validateMeetingSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!MEETING_SORT_PROPERTIES.contains(order.getProperty())
                    && !"createdAt".equals(order.getProperty())) {
                throw new BadRequestException(INVALID_SORT_PROPERTY_MESSAGE);
            }
        }
    }

    private void validateParticipationSort(Pageable pageable) {
        Set<String> allowedProperties = Set.of(
                "id",
                "status",
                "createdAt",
                "updatedAt",
                "reviewedAt",
                "canceledAt"
        );
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BadRequestException(INVALID_SORT_PROPERTY_MESSAGE);
            }
        }
    }

    private Map<Long, List<String>> getImageUrlsByMeetingIds(List<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> imageUrlsByMeetingId = new HashMap<>();
        meetingImageRepository.findByMeetingIdInOrderByMeetingIdAscSortOrderAsc(meetingIds)
                .forEach(image -> imageUrlsByMeetingId
                        .computeIfAbsent(image.getMeeting().getId(), id -> new ArrayList<>())
                        .add(image.getImageUrl()));
        return imageUrlsByMeetingId;
    }
}
