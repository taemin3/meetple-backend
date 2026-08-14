package com.meetple.backend.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.image.service.ImageService;
import com.meetple.backend.domain.meeting.dto.response.MeetingResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingBookmark;
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
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingEngagementServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingParticipationRepository participationRepository;

    @Mock
    private MeetingBookmarkRepository bookmarkRepository;

    @Mock
    private MeetingImageRepository meetingImageRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private MeetingEngagementService engagementService;

    @Test
    void getMyBookmarksIncludesImagesAndTranslatesMeetingSortProperties() {
        Member host = member(1L, "host@meetple.com", "host");
        Member member = member(2L, "member@meetple.com", "member");
        Meeting meeting = meeting(10L, host);
        MeetingBookmark bookmark = MeetingBookmark.create(meeting, member);
        MeetingImage image = MeetingImage.create(meeting, "images/meeting/1/meeting.png", 0);
        PageRequest request = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Order.asc("meetingDate"),
                        Sort.Order.desc("title")
                )
        );
        PageRequest repositoryRequest = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Order.asc("meeting.meetingDate"),
                        Sort.Order.desc("meeting.title")
                )
        );

        given(bookmarkRepository.findByMemberId(2L, repositoryRequest))
                .willReturn(new PageImpl<>(List.of(bookmark), repositoryRequest, 1));
        given(meetingImageRepository.findByMeetingIdInOrderByMeetingIdAscSortOrderAsc(List.of(10L)))
                .willReturn(List.of(image));
        given(imageService.createFileUrl("images/meeting/1/meeting.png"))
                .willReturn("https://cdn.meetple.com/meeting.png");

        PageResponse<MeetingResponse> response = engagementService.getMyBookmarks(2L, request);

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.imageUrls()).containsExactly("https://cdn.meetple.com/meeting.png");
            assertThat(item.thumbnailImageUrl()).isEqualTo("https://cdn.meetple.com/meeting.png");
        });
        verify(bookmarkRepository).findByMemberId(2L, repositoryRequest);
    }

    @Test
    void getMyBookmarksRejectsUnsupportedSortProperty() {
        assertThatThrownBy(() -> engagementService.getMyBookmarks(
                2L,
                PageRequest.of(0, 20, Sort.by("unknown"))
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported sort property.");
    }

    @Test
    void getMyHostedMeetingsIncludesImages() {
        Member host = member(1L, "host@meetple.com", "host");
        Meeting meeting = meeting(10L, host);
        MeetingImage image = MeetingImage.create(meeting, "images/meeting/1/hosted.png", 0);
        PageRequest request = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt")));

        given(meetingRepository.findByHostId(1L, request))
                .willReturn(new PageImpl<>(List.of(meeting), request, 1));
        given(meetingImageRepository.findByMeetingIdInOrderByMeetingIdAscSortOrderAsc(List.of(10L)))
                .willReturn(List.of(image));
        given(imageService.createFileUrl("images/meeting/1/hosted.png"))
                .willReturn("https://cdn.meetple.com/hosted.png");

        PageResponse<MeetingResponse> response = engagementService.getMyHostedMeetings(1L, request);

        assertThat(response.content()).singleElement().satisfies(item ->
                assertThat(item.thumbnailImageUrl()).isEqualTo("https://cdn.meetple.com/hosted.png")
        );
    }

    @Test
    void getMyJoinedMeetingsLoadsApprovedParticipationsAndTranslatesSort() {
        Member host = member(1L, "host@meetple.com", "host");
        Member member = member(2L, "member@meetple.com", "member");
        Meeting meeting = meeting(10L, host);
        MeetingParticipation participation = MeetingParticipation.apply(meeting, member, null);
        participation.approve();
        PageRequest request = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("meetingDate")));
        PageRequest repositoryRequest = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Order.desc("meeting.meetingDate"))
        );

        given(participationRepository.findByMemberIdAndStatus(
                2L,
                ParticipationStatus.APPROVED,
                repositoryRequest
        )).willReturn(new PageImpl<>(List.of(participation), repositoryRequest, 1));
        given(meetingImageRepository.findByMeetingIdInOrderByMeetingIdAscSortOrderAsc(List.of(10L)))
                .willReturn(List.of());

        PageResponse<MeetingResponse> response = engagementService.getMyJoinedMeetings(2L, request);

        assertThat(response.content()).singleElement().extracting(MeetingResponse::id).isEqualTo(10L);
        verify(participationRepository).findByMemberIdAndStatus(
                2L,
                ParticipationStatus.APPROVED,
                repositoryRequest
        );
    }

    @Test
    void getMyApplicationsReturnsApplicationsForCurrentMember() {
        Member host = member(1L, "host@meetple.com", "host");
        Member member = member(2L, "member@meetple.com", "member");
        Meeting meeting = meeting(10L, host);
        MeetingParticipation participation = MeetingParticipation.apply(meeting, member, "Join me");
        ReflectionTestUtils.setField(participation, "id", 100L);
        PageRequest request = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt")));

        given(participationRepository.findByMemberId(2L, request))
                .willReturn(new PageImpl<>(List.of(participation), request, 1));

        var response = engagementService.getMyApplications(2L, request);

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(100L);
            assertThat(item.meetingId()).isEqualTo(10L);
            assertThat(item.meetingTitle()).isEqualTo("Weekend running");
        });
    }

    private Meeting meeting(Long id, Member host) {
        Category category = Category.create("exercise");
        ReflectionTestUtils.setField(category, "id", 1L);
        Meeting meeting = Meeting.create(
                host,
                category,
                "Weekend running",
                "Run together at an easy pace.",
                "Yeouido Park",
                "330 Yeouidong-ro, Yeongdeungpo-gu, Seoul",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500"),
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
}
