package com.meetple.backend.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.meeting.dto.request.CreateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.request.NearbyMeetingSearchRequest;
import com.meetple.backend.domain.meeting.dto.request.UpdateMeetingRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingPageResponse;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.ForbiddenException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private MeetingService meetingService;

    @Test
    void createMeetingReturnsSavedMeetingResponse() {
        Member host = member(1L, "host@meetple.com", "host");
        Category category = category(1L, "exercise");
        CreateMeetingRequest request = createRequest();

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
                .hasMessage("Only the host can change this meeting.");
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
        Meeting farAway = meeting(
                11L,
                member(2L, "far@meetple.com", "far"),
                category,
                new BigDecimal("37.700000"),
                new BigDecimal("127.100000")
        );
        given(meetingRepository.findByStatusAndCoordinateBounds(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).willReturn(List.of(nearby, farAway));

        MeetingPageResponse response = meetingService.getNearbyMeetings(
                new NearbyMeetingSearchRequest(37.5219, 126.9245, 1000, "exercise"),
                PageRequest.of(0, 20)
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.meetings()).extracting(MeetingResponse::id)
                .containsExactly(10L);
    }

    @Test
    void getMeetingsMapsPageResponse() {
        Meeting meeting = meeting(10L, member(1L, "host@meetple.com", "host"), category(1L, "exercise"));
        given(meetingRepository.findByStatus(MeetingStatus.RECRUITING, PageRequest.of(0, 10)))
                .willReturn(new PageImpl<>(List.of(meeting), PageRequest.of(0, 10), 1));

        MeetingPageResponse response = meetingService.getMeetings(
                MeetingStatus.RECRUITING,
                PageRequest.of(0, 10)
        );

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.meetings()).extracting(MeetingResponse::title)
                .containsExactly("Weekend running");
    }

    private CreateMeetingRequest createRequest() {
        return new CreateMeetingRequest(
                "Weekend running",
                "exercise",
                "Yeouido Park",
                "330 Yeouidong-ro, Yeongdeungpo-gu, Seoul",
                37.5219,
                126.9245,
                LocalDateTime.now().plusDays(7),
                10,
                "Run together at an easy pace."
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
        Category category = Category.create(name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
