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
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {

    private static final String MEMBER_NOT_FOUND_MESSAGE = "회원을 찾을 수 없습니다.";
    private static final String CATEGORY_NOT_FOUND_MESSAGE = "카테고리를 찾을 수 없습니다.";
    private static final String MEETING_NOT_FOUND_MESSAGE = "모임을 찾을 수 없습니다.";
    private static final String MEETING_FORBIDDEN_MESSAGE = "모임 변경은 주최자만 할 수 있습니다.";
    private static final String CLOSED_MEETING_MESSAGE = "마감된 모임은 변경할 수 없습니다.";
    private static final String CAPACITY_TOO_SMALL_MESSAGE = "정원은 현재 인원보다 적을 수 없습니다.";
    private static final String INVALID_MEETING_STATUS_MESSAGE = "지원하지 않는 모임 상태입니다.";
    private static final String INVALID_SORT_PROPERTY_MESSAGE = "지원하지 않는 정렬 조건입니다.";
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id",
            "title",
            "meetingDate",
            "currentPeople",
            "maxPeople",
            "status",
            "createdAt",
            "updatedAt"
    );

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

    public PageResponse<MeetingResponse> getMeetings(String status, Pageable pageable) {
        validateSort(pageable);
        MeetingStatus meetingStatus = parseStatus(status);

        Page<MeetingResponse> meetings = (meetingStatus == null
                ? meetingRepository.findAll(pageable)
                : meetingRepository.findByStatus(meetingStatus, pageable))
                .map(MeetingResponse::from);

        return PageResponse.from(meetings);
    }

    public PageResponse<MeetingResponse> getNearbyMeetings(NearbyMeetingSearchRequest request, Pageable pageable) {
        CoordinateBounds bounds = CoordinateBounds.from(
                request.latitude(),
                request.longitude(),
                request.radiusMeters()
        );

        Page<MeetingResponse> page = meetingRepository.findNearbyMeetings(
                        MeetingStatus.RECRUITING.name(),
                        bounds.minLatitude(),
                        bounds.maxLatitude(),
                        bounds.minLongitude(),
                        bounds.maxLongitude(),
                        bounds.crossesAntimeridian(),
                        normalizeOptionalText(request.category()),
                        request.latitude(),
                        request.longitude(),
                        request.radiusMeters(),
                        EARTH_RADIUS_METERS,
                        withoutSort(pageable)
                )
                .map(MeetingResponse::from);
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

    private MeetingStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }

        try {
            return MeetingStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(INVALID_MEETING_STATUS_MESSAGE);
        }
    }

    private void validateSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new BadRequestException(INVALID_SORT_PROPERTY_MESSAGE);
            }
        }
    }

    private Pageable withoutSort(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BigDecimal toBigDecimal(Double value) {
        return BigDecimal.valueOf(value);
    }

    private record CoordinateBounds(
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude,
            boolean crossesAntimeridian
    ) {

        private static CoordinateBounds from(double latitude, double longitude, int radiusMeters) {
            double latitudeDelta = radiusMeters / METERS_PER_LATITUDE_DEGREE;
            double longitudeScale = Math.cos(Math.toRadians(latitude));
            double longitudeDelta = Math.abs(longitudeScale) < 0.000001
                    ? 180.0
                    : radiusMeters / (METERS_PER_LATITUDE_DEGREE * longitudeScale);
            double rawMinLongitude = longitude - Math.abs(longitudeDelta);
            double rawMaxLongitude = longitude + Math.abs(longitudeDelta);
            boolean crossesAntimeridian = rawMinLongitude < -180.0 || rawMaxLongitude > 180.0;

            return new CoordinateBounds(
                    BigDecimal.valueOf(Math.max(-90.0, latitude - latitudeDelta)),
                    BigDecimal.valueOf(Math.min(90.0, latitude + latitudeDelta)),
                    BigDecimal.valueOf(crossesAntimeridian
                            ? normalizeLongitude(rawMinLongitude)
                            : Math.max(-180.0, rawMinLongitude)),
                    BigDecimal.valueOf(crossesAntimeridian
                            ? normalizeLongitude(rawMaxLongitude)
                            : Math.min(180.0, rawMaxLongitude)),
                    crossesAntimeridian
            );
        }

        private static double normalizeLongitude(double longitude) {
            double normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
            return normalized == -180.0 ? 180.0 : normalized;
        }
    }
}
