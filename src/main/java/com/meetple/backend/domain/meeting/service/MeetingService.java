package com.meetple.backend.domain.meeting.service;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.meeting.dto.request.CreateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.request.NearbyMeetingSearchRequest;
import com.meetple.backend.domain.meeting.dto.request.UpdateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {

    private static final String MEMBER_NOT_FOUND_MESSAGE = "Member not found.";
    private static final String CATEGORY_NOT_FOUND_MESSAGE = "Category not found.";
    private static final String MEETING_NOT_FOUND_MESSAGE = "Meeting not found.";
    private static final String MEETING_FORBIDDEN_MESSAGE = "Only the host can change this meeting.";
    private static final String CLOSED_MEETING_MESSAGE = "Closed meetings cannot be changed.";
    private static final String CAPACITY_TOO_SMALL_MESSAGE = "Capacity cannot be less than current people.";
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;

    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public MeetingResponse createMeeting(Long memberId, CreateMeetingRequest request) {
        Member host = getMember(memberId);
        Category category = getCategory(request.category());

        Meeting meeting = Meeting.create(
                host,
                category,
                request.title().trim(),
                request.description().trim(),
                request.locationName().trim(),
                request.address().trim(),
                toBigDecimal(request.latitude()),
                toBigDecimal(request.longitude()),
                request.capacity(),
                request.scheduledAt(),
                null
        );

        return MeetingResponse.from(meetingRepository.save(meeting));
    }

    public PageResponse<MeetingResponse> getMeetings(MeetingStatus status, Pageable pageable) {
        Page<MeetingResponse> meetings = (status == null
                ? meetingRepository.findAll(pageable)
                : meetingRepository.findByStatus(status, pageable))
                .map(MeetingResponse::from);

        return PageResponse.from(meetings);
    }

    public PageResponse<MeetingResponse> getNearbyMeetings(NearbyMeetingSearchRequest request, Pageable pageable) {
        CoordinateBounds bounds = CoordinateBounds.from(
                request.latitude(),
                request.longitude(),
                request.radiusMeters()
        );

        List<NearbyMeeting> nearbyMeetings = meetingRepository.findByStatusAndCoordinateBounds(
                        MeetingStatus.RECRUITING,
                        bounds.minLatitude(),
                        bounds.maxLatitude(),
                        bounds.minLongitude(),
                        bounds.maxLongitude(),
                        normalizeOptionalText(request.category())
                )
                .stream()
                .map(meeting -> new NearbyMeeting(
                        meeting,
                        calculateDistanceMeters(
                                request.latitude(),
                                request.longitude(),
                                meeting.getLatitude().doubleValue(),
                                meeting.getLongitude().doubleValue()
                        )
                ))
                .filter(nearbyMeeting -> nearbyMeeting.distanceMeters() <= request.radiusMeters())
                .sorted(Comparator.comparingDouble(NearbyMeeting::distanceMeters))
                .toList();

        Page<MeetingResponse> page = toPage(
                nearbyMeetings.stream()
                        .map(NearbyMeeting::meeting)
                        .map(MeetingResponse::from)
                        .toList(),
                pageable
        );
        return PageResponse.from(page);
    }

    public MeetingResponse getMeeting(Long meetingId) {
        return MeetingResponse.from(getMeetingEntity(meetingId));
    }

    @Transactional
    public MeetingResponse updateMeeting(Long memberId, Long meetingId, UpdateMeetingRequest request) {
        Meeting meeting = getMeetingEntity(meetingId);
        ensureHost(meeting, memberId);
        ensureOpen(meeting);

        Category category = request.category() == null
                ? meeting.getCategory()
                : getCategory(request.category());
        Integer capacity = request.capacity() == null
                ? meeting.getMaxPeople()
                : request.capacity();
        if (capacity < meeting.getCurrentPeople()) {
            throw new BadRequestException(CAPACITY_TOO_SMALL_MESSAGE);
        }

        meeting.update(
                category,
                chooseText(request.title(), meeting.getTitle(), "Title is required."),
                chooseText(request.description(), meeting.getContent(), "Description is required."),
                chooseText(request.locationName(), meeting.getLocationName(), "Location name is required."),
                chooseText(request.address(), meeting.getAddress(), "Address is required."),
                request.latitude() == null ? meeting.getLatitude() : toBigDecimal(request.latitude()),
                request.longitude() == null ? meeting.getLongitude() : toBigDecimal(request.longitude()),
                capacity,
                chooseDateTime(request.scheduledAt(), meeting.getMeetingDate())
        );

        return MeetingResponse.from(meeting);
    }

    @Transactional
    public void deleteMeeting(Long memberId, Long meetingId) {
        cancelMeeting(memberId, meetingId);
    }

    @Transactional
    public MeetingResponse completeMeeting(Long memberId, Long meetingId) {
        Meeting meeting = getMeetingEntity(meetingId);
        ensureHost(meeting, memberId);
        ensureOpen(meeting);

        meeting.complete();
        return MeetingResponse.from(meeting);
    }

    @Transactional
    public MeetingResponse cancelMeeting(Long memberId, Long meetingId) {
        Meeting meeting = getMeetingEntity(meetingId);
        ensureHost(meeting, memberId);
        ensureOpen(meeting);

        meeting.cancel();
        return MeetingResponse.from(meeting);
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
    }

    private Category getCategory(String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            throw new BadRequestException("Category is required.");
        }
        return categoryRepository.findByName(categoryName.trim())
                .orElseThrow(() -> new NotFoundException(CATEGORY_NOT_FOUND_MESSAGE));
    }

    private Meeting getMeetingEntity(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException(MEETING_NOT_FOUND_MESSAGE));
    }

    private void ensureHost(Meeting meeting, Long memberId) {
        if (!meeting.isHostedBy(memberId)) {
            throw new ForbiddenException(MEETING_FORBIDDEN_MESSAGE);
        }
    }

    private void ensureOpen(Meeting meeting) {
        if (meeting.isClosed()) {
            throw new BadRequestException(CLOSED_MEETING_MESSAGE);
        }
    }

    private String chooseText(String value, String currentValue, String message) {
        if (value == null) {
            return currentValue;
        }
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private LocalDateTime chooseDateTime(LocalDateTime value, LocalDateTime currentValue) {
        return value == null ? currentValue : value;
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal toBigDecimal(Double value) {
        return BigDecimal.valueOf(value);
    }

    private Page<MeetingResponse> toPage(List<MeetingResponse> meetings, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(meetings);
        }

        int start = (int) Math.min(pageable.getOffset(), meetings.size());
        int end = Math.min(start + pageable.getPageSize(), meetings.size());
        return new PageImpl<>(meetings.subList(start, end), pageable, meetings.size());
    }

    private double calculateDistanceMeters(
            double latitude,
            double longitude,
            double targetLatitude,
            double targetLongitude
    ) {
        double latitudeDistance = Math.toRadians(targetLatitude - latitude);
        double longitudeDistance = Math.toRadians(targetLongitude - longitude);
        double originLatitude = Math.toRadians(latitude);
        double destinationLatitude = Math.toRadians(targetLatitude);

        double a = Math.pow(Math.sin(latitudeDistance / 2), 2)
                + Math.cos(originLatitude)
                * Math.cos(destinationLatitude)
                * Math.pow(Math.sin(longitudeDistance / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private record NearbyMeeting(Meeting meeting, double distanceMeters) {
    }

    private record CoordinateBounds(
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    ) {

        private static CoordinateBounds from(double latitude, double longitude, int radiusMeters) {
            double latitudeDelta = radiusMeters / METERS_PER_LATITUDE_DEGREE;
            double longitudeScale = Math.cos(Math.toRadians(latitude));
            double longitudeDelta = Math.abs(longitudeScale) < 0.000001
                    ? 180.0
                    : radiusMeters / (METERS_PER_LATITUDE_DEGREE * longitudeScale);

            return new CoordinateBounds(
                    BigDecimal.valueOf(Math.max(-90.0, latitude - latitudeDelta)),
                    BigDecimal.valueOf(Math.min(90.0, latitude + latitudeDelta)),
                    BigDecimal.valueOf(Math.max(-180.0, longitude - Math.abs(longitudeDelta))),
                    BigDecimal.valueOf(Math.min(180.0, longitude + Math.abs(longitudeDelta)))
            );
        }
    }
}
