package com.meetple.backend.domain.meeting.service;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.image.entity.ImageUploadPurpose;
import com.meetple.backend.domain.image.service.ImageDeletionService;
import com.meetple.backend.domain.image.service.ImageService;
import com.meetple.backend.domain.meeting.dto.request.CreateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.request.MeetingSearchRequest;
import com.meetple.backend.domain.meeting.dto.request.NearbyMeetingSearchRequest;
import com.meetple.backend.domain.meeting.dto.request.UpdateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingSummaryResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingImage;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.repository.MeetingImageRepository;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.context.ApplicationEventPublisher;
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
    private static final int NOTIFICATION_MESSAGE_MAX_LENGTH = 500;
    private static final long UNKNOWN_END_AUTO_COMPLETE_HOURS = 24;
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
    private final MeetingImageRepository meetingImageRepository;
    private final MemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final MeetingParticipationRepository participationRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageService imageService;
    private final ImageDeletionService imageDeletionService;

    @Transactional
    public MeetingResponse createMeeting(Long memberId, CreateMeetingRequest request) {
        Member host = getMember(memberId);
        Category category = getCategory(request.category());
        List<ImageReference> images = normalizeImages(memberId, request.imageObjectKeys());

        LocalDateTime endDate = validateEndDate(request.scheduledAt(), request.endsAt());
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
                endDate,
                firstImageObjectKey(images)
        );

        Meeting savedMeeting = meetingRepository.save(meeting);
        saveMeetingImages(savedMeeting, images);

        return toResponse(savedMeeting, images);
    }

    public PageResponse<MeetingResponse> getMeetings(String status, Pageable pageable) {
        validateSort(pageable);
        MeetingStatus meetingStatus = parseStatus(status);

        Page<Meeting> meetings = (meetingStatus == null
                ? meetingRepository.findAll(pageable)
                : meetingRepository.findByStatus(meetingStatus, pageable));

        return PageResponse.from(toResponsePage(meetings));
    }

    public PageResponse<MeetingSummaryResponse> getMeetingSummaries(String status, Pageable pageable) {
        validateSort(pageable);
        MeetingStatus meetingStatus = parseStatus(status);

        Page<Meeting> meetings = (meetingStatus == null
                ? meetingRepository.findAll(pageable)
                : meetingRepository.findByStatus(meetingStatus, pageable));

        return PageResponse.from(meetings.map(this::toSummaryResponse));
    }

    public PageResponse<MeetingResponse> getNearbyMeetings(NearbyMeetingSearchRequest request, Pageable pageable) {
        CoordinateBounds bounds = CoordinateBounds.from(
                request.latitude(),
                request.longitude(),
                request.radiusMeters()
        );

        Page<Meeting> page = meetingRepository.findNearbyMeetings(
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
                );
        return PageResponse.from(toResponsePage(page));
    }

    public PageResponse<MeetingResponse> searchMeetings(MeetingSearchRequest request, Pageable pageable) {
        Page<Long> meetingIds = meetingRepository.searchMeetingIds(
                MeetingStatus.RECRUITING.name(),
                toLiteralLikePattern(request.keyword()),
                normalizeOptionalText(request.category()),
                request.latitude(),
                request.longitude(),
                EARTH_RADIUS_METERS,
                withoutSort(pageable)
        );
        return PageResponse.from(toResponsePage(loadSearchMeetings(meetingIds)));
    }

    public MeetingResponse getMeeting(Long meetingId) {
        return toResponse(getMeetingEntity(meetingId));
    }

    @Transactional
    public MeetingResponse updateMeeting(Long memberId, Long meetingId, UpdateMeetingRequest request) {
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        ensureHost(meeting, memberId);
        ensureOpen(meeting);

        boolean imagesProvided = request.getImageObjectKeys() != null;
        List<ImageReference> images = imagesProvided
                ? normalizeImages(memberId, request.getImageObjectKeys())
                : null;

        Category category = request.getCategory() == null
                ? meeting.getCategory()
                : getCategory(request.getCategory());
        Integer capacity = request.getCapacity() == null
                ? meeting.getMaxPeople()
                : request.getCapacity();
        if (capacity < meeting.getCurrentPeople()) {
            throw new BadRequestException(CAPACITY_TOO_SMALL_MESSAGE);
        }

        meeting.update(
                category,
                chooseText(request.getTitle(), meeting.getTitle(), "Title is required."),
                chooseText(request.getDescription(), meeting.getContent(), "Description is required."),
                chooseText(request.getLocationName(), meeting.getLocationName(), "Location name is required."),
                chooseText(request.getAddress(), meeting.getAddress(), "Address is required."),
                request.getLatitude() == null ? meeting.getLatitude() : toBigDecimal(request.getLatitude()),
                request.getLongitude() == null ? meeting.getLongitude() : toBigDecimal(request.getLongitude()),
                capacity,
                chooseDateTime(request.getScheduledAt(), meeting.getMeetingDate()),
                resolveUpdatedEndDate(meeting, request)
        );

        if (images != null) {
            meeting.changeThumbnailImageObjectKey(firstImageObjectKey(images));
            replaceMeetingImages(meeting, images);
            return toResponse(meeting, images);
        }

        return toResponse(meeting);
    }

    @Transactional
    public void deleteMeeting(Long memberId, Long meetingId) {
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        ensureHost(meeting, memberId);
        ensureOpen(meeting);
        if (participationRepository.existsByMeetingId(meetingId)) {
            throw new BadRequestException("참여 신청 내역이 있는 모임은 삭제할 수 없습니다.");
        }
        meeting.softDelete(LocalDateTime.now());
    }

    @Transactional
    public MeetingResponse completeMeeting(Long memberId, Long meetingId) {
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        ensureHost(meeting, memberId);
        ensureOpen(meeting);
        if (LocalDateTime.now().isBefore(meeting.getMeetingDate())) {
            throw new BadRequestException("모임 시작 이후에만 완료할 수 있습니다.");
        }

        meeting.complete();
        return toResponse(meeting);
    }

    @Transactional
    public MeetingResponse cancelMeeting(Long memberId, Long meetingId, String reason) {
        Meeting meeting = getMeetingEntityForUpdate(meetingId);
        ensureHost(meeting, memberId);
        ensureOpen(meeting);

        String normalizedReason = normalizeRequiredText(reason, "모임 취소 사유를 입력해주세요.");
        meeting.cancel(normalizedReason);
        eventPublisher.publishEvent(
                ChatSessionInvalidationEvent.meetingCanceled(meetingId)
        );
        participationRepository.findByMeetingIdAndStatus(meetingId, ParticipationStatus.APPROVED)
                .forEach(participation -> notificationService.notify(
                        participation.getMember(),
                        "MEETING_CANCELED",
                        "모임 취소",
                        cancellationNotificationMessage(meeting.getTitle(), normalizedReason),
                        meetingId
                ));
        return toResponse(meeting);
    }

    @Transactional
    public MeetingResponse cancelMeeting(Long memberId, Long meetingId) {
        return cancelMeeting(memberId, meetingId, "모임장이 모임을 취소했습니다.");
    }

    @Transactional
    public int completeEndedMeetings(LocalDateTime now) {
        List<MeetingStatus> openStatuses = List.of(MeetingStatus.RECRUITING, MeetingStatus.FULL);
        List<Meeting> endedMeetings = new ArrayList<>(meetingRepository.findByStatusInAndEndDateLessThanEqual(
                openStatuses,
                now
        ));
        endedMeetings.addAll(
                meetingRepository.findByStatusInAndEndDateIsNullAndMeetingDateLessThanEqual(
                        openStatuses,
                        now.minusHours(UNKNOWN_END_AUTO_COMPLETE_HOURS)
                )
        );
        endedMeetings.forEach(Meeting::complete);
        return endedMeetings.size();
    }

    private void saveMeetingImages(Meeting meeting, List<ImageReference> images) {
        if (images.isEmpty()) {
            return;
        }

        List<MeetingImage> meetingImages = IntStream.range(0, images.size())
                .mapToObj(index -> MeetingImage.create(
                        meeting,
                        images.get(index).objectKey(),
                        index
                ))
                .toList();
        meetingImageRepository.saveAll(meetingImages);
    }

    private void replaceMeetingImages(Meeting meeting, List<ImageReference> images) {
        Set<String> replacementObjectKeys = images.stream()
                .map(ImageReference::objectKey)
                .collect(java.util.stream.Collectors.toSet());
        List<String> removedObjectKeys = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(meeting.getThumbnailImageObjectKey()),
                        meetingImageRepository.findByMeetingIdOrderBySortOrderAsc(meeting.getId())
                                .stream()
                                .map(MeetingImage::getObjectKey)
                )
                .filter(StringUtils::hasText)
                .distinct()
                .filter(objectKey -> !replacementObjectKeys.contains(objectKey))
                .toList();
        meetingImageRepository.deleteByMeetingId(meeting.getId());
        saveMeetingImages(meeting, images);
        imageDeletionService.schedule(removedObjectKeys);
    }

    private MeetingResponse toResponse(Meeting meeting) {
        List<ImageReference> images = meetingImageRepository.findByMeetingIdOrderBySortOrderAsc(meeting.getId())
                .stream()
                .filter(image -> StringUtils.hasText(image.getObjectKey()))
                .map(this::toImageReference)
                .toList();
        return toResponse(meeting, images);
    }

    private MeetingSummaryResponse toSummaryResponse(Meeting meeting) {
        String thumbnailImageUrl = StringUtils.hasText(meeting.getThumbnailImageObjectKey())
                ? imageService.createFileUrl(meeting.getThumbnailImageObjectKey())
                : meeting.getCategory().getDefaultImageUrl();
        return MeetingSummaryResponse.from(meeting, thumbnailImageUrl);
    }

    private Page<MeetingResponse> toResponsePage(Page<Meeting> meetings) {
        Map<Long, List<ImageReference>> imagesByMeetingId = getImagesByMeetingIds(
                meetings.getContent()
                        .stream()
                        .map(Meeting::getId)
                        .toList()
        );
        return meetings.map(meeting -> toResponse(
                meeting,
                imagesByMeetingId.getOrDefault(meeting.getId(), List.of())
        ));
    }

    private Map<Long, List<ImageReference>> getImagesByMeetingIds(Collection<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<ImageReference>> imagesByMeetingId = new HashMap<>();
        meetingImageRepository.findByMeetingIdInOrderByMeetingIdAscSortOrderAsc(meetingIds)
                .stream()
                .filter(image -> StringUtils.hasText(image.getObjectKey()))
                .forEach(image -> imagesByMeetingId
                        .computeIfAbsent(image.getMeeting().getId(), id -> new java.util.ArrayList<>())
                        .add(toImageReference(image)));
        return imagesByMeetingId;
    }

    private Page<Meeting> loadSearchMeetings(Page<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return new PageImpl<>(List.of(), meetingIds.getPageable(), meetingIds.getTotalElements());
        }

        Map<Long, Meeting> meetingsById = new HashMap<>();
        meetingRepository.findAllWithHostAndCategoryByIdIn(meetingIds.getContent())
                .forEach(meeting -> meetingsById.put(meeting.getId(), meeting));
        List<Meeting> orderedMeetings = meetingIds.getContent().stream()
                .map(meetingsById::get)
                .filter(Objects::nonNull)
                .toList();
        return new PageImpl<>(orderedMeetings, meetingIds.getPageable(), meetingIds.getTotalElements());
    }

    private List<ImageReference> normalizeImages(Long memberId, List<String> imageObjectKeys) {
        if (imageObjectKeys != null) {
            return imageObjectKeys.stream()
                    .map(objectKey -> imageService.resolveOwnedObjectKey(
                            memberId,
                            ImageUploadPurpose.MEETING,
                            objectKey
                    ))
                    .map(objectKey -> new ImageReference(objectKey, imageService.createFileUrl(objectKey)))
                    .toList();
        }
        return List.of();
    }

    private MeetingResponse toResponse(Meeting meeting, List<ImageReference> images) {
        return MeetingResponse.from(
                meeting,
                images.stream().map(ImageReference::fileUrl).toList(),
                images.stream()
                        .map(ImageReference::objectKey)
                        .toList(),
                imageService.createFileUrl(meeting.getHost().getProfileImageObjectKey())
        );
    }

    private ImageReference toImageReference(MeetingImage image) {
        return new ImageReference(
                image.getObjectKey(),
                imageService.createFileUrl(image.getObjectKey())
        );
    }

    private String firstImageObjectKey(List<ImageReference> images) {
        return images.isEmpty() ? null : images.get(0).objectKey();
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

    private Meeting getMeetingEntityForUpdate(Long meetingId) {
        return meetingRepository.findByIdForUpdate(meetingId)
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

    private LocalDateTime validateEndDate(LocalDateTime startDate, LocalDateTime endDate) {
        if (endDate != null && !endDate.isAfter(startDate)) {
            throw new BadRequestException("모임 종료 시각은 시작 시각 이후여야 합니다.");
        }
        return endDate;
    }

    private LocalDateTime resolveUpdatedEndDate(Meeting meeting, UpdateMeetingRequest request) {
        LocalDateTime startDate = chooseDateTime(request.getScheduledAt(), meeting.getMeetingDate());
        LocalDateTime endDate = request.isEndsAtProvided()
                ? request.getEndsAt()
                : meeting.getEndDate();
        return validateEndDate(startDate, endDate);
    }

    private String normalizeRequiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String cancellationNotificationMessage(String meetingTitle, String reason) {
        String message = meetingTitle + " 모임이 취소되었습니다. 사유: " + reason;
        return message.length() <= NOTIFICATION_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, NOTIFICATION_MESSAGE_MAX_LENGTH);
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

    private String toLiteralLikePattern(String value) {
        String escaped = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
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

    private record ImageReference(String objectKey, String fileUrl) {
    }
}
