package com.meetple.backend.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.meeting.dto.request.CreateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.request.NearbyMeetingSearchRequest;
import com.meetple.backend.domain.meeting.dto.request.UpdateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingImage;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingBookmarkRepository;
import com.meetple.backend.domain.meeting.repository.MeetingImageRepository;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.response.PageResponse;
import com.meetple.backend.global.websocket.ChatAccessRevocationReason;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingImageRepository meetingImageRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MeetingParticipationRepository participationRepository;

    @Mock
    private MeetingBookmarkRepository bookmarkRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MeetingService meetingService;

    @Test
    void createMeetingReturnsSavedMeetingResponse() {
        Member host = member(1L, "host@meetple.com", "host");
        Category category = category(1L, "exercise");
        CreateMeetingRequest request = createRequestWithImageUrls(List.of(
                "https://cdn.meetple.com/images/meeting/1/first.png",
                "https://cdn.meetple.com/images/meeting/1/second.png"
        ));

        given(memberRepository.findById(1L)).willReturn(Optional.of(host));
        given(categoryRepository.findByName("exercise")).willReturn(Optional.of(category));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> {
            Meeting meeting = invocation.getArgument(0);
            ReflectionTestUtils.setField(meeting, "id", 10L);
            return meeting;
        });

        MeetingResponse response = meetingService.createMeeting(1L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.hostId()).isEqualTo(1L);
        assertThat(response.categoryName()).isEqualTo("exercise");
        assertThat(response.title()).isEqualTo("Weekend running");
        assertThat(response.capacity()).isEqualTo(10);
        assertThat(response.currentPeople()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(MeetingStatus.RECRUITING);
        assertThat(response.thumbnailImageUrl()).isEqualTo("https://cdn.meetple.com/images/meeting/1/first.png");
        assertThat(response.imageUrls()).containsExactly(
                "https://cdn.meetple.com/images/meeting/1/first.png",
                "https://cdn.meetple.com/images/meeting/1/second.png"
        );

        ArgumentCaptor<List<MeetingImage>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(meetingImageRepository).saveAll(imagesCaptor.capture());
        assertThat(imagesCaptor.getValue())
                .extracting(MeetingImage::getImageUrl)
                .containsExactly(
                        "https://cdn.meetple.com/images/meeting/1/first.png",
                        "https://cdn.meetple.com/images/meeting/1/second.png"
                );
    }

    @Test
    void createMeetingUsesCategoryDefaultImageWhenNoImagesAreProvided() {
        Member host = member(1L, "host@meetple.com", "host");
        Category category = category(
                1L,
                "exercise",
                "https://cdn.meetple.com/categories/exercise.png"
        );
        CreateMeetingRequest request = createRequestWithImageUrls(List.of());

        given(memberRepository.findById(1L)).willReturn(Optional.of(host));
        given(categoryRepository.findByName("exercise")).willReturn(Optional.of(category));
        given(meetingRepository.save(any(Meeting.class))).willAnswer(invocation -> {
            Meeting meeting = invocation.getArgument(0);
            ReflectionTestUtils.setField(meeting, "id", 10L);
            return meeting;
        });

        MeetingResponse response = meetingService.createMeeting(1L, request);

        assertThat(response.thumbnailImageUrl())
                .isEqualTo("https://cdn.meetple.com/categories/exercise.png");
        assertThat(response.imageUrls()).isEmpty();
        verify(meetingImageRepository, never()).saveAll(any());
    }

    @Test
    void updateMeetingReplacesImagesWhenImageUrlsAreProvided() {
        Meeting meeting = meeting(10L, member(1L, "host@meetple.com", "host"), category(1L, "exercise"));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        UpdateMeetingRequest request = new UpdateMeetingRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("https://cdn.meetple.com/images/meeting/1/updated.png")
        );

        MeetingResponse response = meetingService.updateMeeting(1L, 10L, request);

        assertThat(response.thumbnailImageUrl()).isEqualTo("https://cdn.meetple.com/images/meeting/1/updated.png");
        assertThat(response.imageUrls()).containsExactly("https://cdn.meetple.com/images/meeting/1/updated.png");
        verify(meetingImageRepository).deleteByMeetingId(10L);
    }

    @Test
    void updateMeetingUsesCategoryDefaultImageWhenImagesAreCleared() {
        Meeting meeting = meeting(
                10L,
                member(1L, "host@meetple.com", "host"),
                category(1L, "exercise", "https://cdn.meetple.com/categories/exercise.png")
        );
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        UpdateMeetingRequest request = new UpdateMeetingRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );

        MeetingResponse response = meetingService.updateMeeting(1L, 10L, request);

        assertThat(response.thumbnailImageUrl())
                .isEqualTo("https://cdn.meetple.com/categories/exercise.png");
        assertThat(response.imageUrls()).isEmpty();
        verify(meetingImageRepository).deleteByMeetingId(10L);
    }

    @Test
    void updateMeetingRejectsNonHost() {
        Meeting meeting = meeting(10L, member(1L, "host@meetple.com", "host"), category(1L, "exercise"));
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        UpdateMeetingRequest request = new UpdateMeetingRequest(
                "Updated title",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> meetingService.updateMeeting(2L, 10L, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("모임 변경은 주최자만 할 수 있습니다.");
    }

    @Test
    void getNearbyMeetingsReturnsOnlyMeetingsInsideRadius() {
        Category category = category(1L, "exercise");
        Meeting nearby = meeting(
                10L,
                member(1L, "host@meetple.com", "host"),
                category,
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500")
        );
        given(meetingRepository.findNearbyMeetings(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any(),
                anyDouble(),
                anyDouble(),
                anyInt(),
                anyDouble(),
                any()
        )).willReturn(new PageImpl<>(List.of(nearby), PageRequest.of(0, 20), 1));
        given(meetingImageRepository.findByMeetingIdInOrderByMeetingIdAscSortOrderAsc(any()))
                .willReturn(List.of(
                        MeetingImage.create(
                                nearby,
                                "https://cdn.meetple.com/images/meeting/10/first.png",
                                0
                        ),
                        MeetingImage.create(
                                nearby,
                                "https://cdn.meetple.com/images/meeting/10/second.png",
                                1
                        )
                ));

        PageResponse<MeetingResponse> response = meetingService.getNearbyMeetings(
                new NearbyMeetingSearchRequest(37.5219, 126.9245, 1000, "exercise"),
                PageRequest.of(0, 20)
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).extracting(MeetingResponse::id)
                .containsExactly(10L);
        assertThat(response.content().getFirst().thumbnailImageUrl())
                .isEqualTo("https://cdn.meetple.com/images/meeting/10/first.png");
        assertThat(response.content().getFirst().imageUrls()).containsExactly(
                "https://cdn.meetple.com/images/meeting/10/first.png",
                "https://cdn.meetple.com/images/meeting/10/second.png"
        );
    }

    @Test
    void getMeetingsMapsPageResponse() {
        Meeting meeting = meeting(10L, member(1L, "host@meetple.com", "host"), category(1L, "exercise"));
        given(meetingRepository.findByStatus(MeetingStatus.RECRUITING, PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(meeting), PageRequest.of(0, 10), 1));

        PageResponse<MeetingResponse> response = meetingService.getMeetings(
                "RECRUITING",
                PageRequest.of(0, 10)
        );

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).extracting(MeetingResponse::title)
                .containsExactly("Weekend running");
    }

    @Test
    void getMeetingsRejectsInvalidStatus() {
        assertThatThrownBy(() -> meetingService.getMeetings("OPEN", PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("지원하지 않는 모임 상태입니다.");
    }

    @Test
    void getMeetingsRejectsInvalidSortProperty() {
        assertThatThrownBy(() -> meetingService.getMeetings(
                "RECRUITING",
                PageRequest.of(0, 10, Sort.by("unknown"))
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("지원하지 않는 정렬 조건입니다.");
    }

    @Test
    void cancelMeetingTruncatesNotificationMessageToColumnLength() {
        Member host = member(1L, "host@meetple.com", "host");
        Member participant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host, category(1L, "exercise"));
        MeetingParticipation participation = MeetingParticipation.apply(meeting, participant, null);
        participation.approve();

        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(participationRepository.findByMeetingIdAndStatus(10L, ParticipationStatus.APPROVED))
                .willReturn(List.of(participation));

        meetingService.cancelMeeting(1L, 10L, "a".repeat(500));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(
                eq(participant),
                eq("MEETING_CANCELED"),
                eq("모임 취소"),
                messageCaptor.capture(),
                eq(10L)
        );
        assertThat(messageCaptor.getValue()).hasSize(500);
        ArgumentCaptor<ChatSessionInvalidationEvent> eventCaptor = ArgumentCaptor.forClass(
                ChatSessionInvalidationEvent.class
        );
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().roomId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().reason())
                .isEqualTo(ChatAccessRevocationReason.MEETING_CANCELED);
    }

    private CreateMeetingRequest createRequest() {
        return createRequestWithImageUrls(List.of(
                "https://cdn.meetple.com/images/meeting/1/first.png",
                "https://cdn.meetple.com/images/meeting/1/second.png"
        ));
    }

    private CreateMeetingRequest createRequestWithImageUrls(List<String> imageUrls) {
        return new CreateMeetingRequest(
                "Weekend running",
                "exercise",
                "Yeouido Park",
                "330 Yeouidong-ro, Yeongdeungpo-gu, Seoul",
                37.5219,
                126.9245,
                LocalDateTime.now().plusDays(7),
                10,
                "Run together at an easy pace.",
                imageUrls
        );
    }

    private Meeting meeting(Long id, Member host, Category category) {
        return meeting(
                id,
                host,
                category,
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500")
        );
    }

    private Meeting meeting(Long id, Member host, Category category, BigDecimal latitude, BigDecimal longitude) {
        Meeting meeting = Meeting.create(
                host,
                category,
                "Weekend running",
                "Run together at an easy pace.",
                "Yeouido Park",
                "330 Yeouidong-ro, Yeongdeungpo-gu, Seoul",
                latitude,
                longitude,
                10,
                LocalDateTime.now().plusDays(7),
                null
        );
        ReflectionTestUtils.setField(meeting, "id", id);
        return meeting;
    }

    private Member member(Long id, String email, String nickname) {
        Member member = Member.createUser(email, "encoded-password", nickname, "Seoul");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Category category(Long id, String name) {
        return category(id, name, null);
    }

    private Category category(Long id, String name, String defaultImageUrl) {
        Category category = Category.create(name, defaultImageUrl);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
