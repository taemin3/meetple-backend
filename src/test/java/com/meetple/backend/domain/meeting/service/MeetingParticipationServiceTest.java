package com.meetple.backend.domain.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.meeting.dto.request.CreateMeetingParticipationRequest;
import com.meetple.backend.domain.meeting.dto.response.MeetingParticipationResponse;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingParticipationServiceTest {

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MeetingParticipationRepository participationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MeetingParticipationService participationService;

    @Test
    void applyParticipationReturnsPendingResponse() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);

        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(memberRepository.findById(2L)).willReturn(Optional.of(applicant));
        given(participationRepository.findByMeetingIdAndMemberIdForUpdate(10L, 2L)).willReturn(Optional.empty());
        given(participationRepository.saveAndFlush(any(MeetingParticipation.class))).willAnswer(invocation -> {
            MeetingParticipation participation = invocation.getArgument(0);
            ReflectionTestUtils.setField(participation, "id", 100L);
            return participation;
        });

        MeetingParticipationResponse response = participationService.applyParticipation(
                2L,
                10L,
                new CreateMeetingParticipationRequest("  I want to join.  ")
        );

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.meetingId()).isEqualTo(10L);
        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(ParticipationStatus.PENDING);
        assertThat(response.message()).isEqualTo("I want to join.");
    }

    @Test
    void applyParticipationRejectsHost() {
        Member host = member(1L, "host@meetple.com", "host");
        Meeting meeting = meeting(10L, host);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(memberRepository.findById(1L)).willReturn(Optional.of(host));

        assertThatThrownBy(() -> participationService.applyParticipation(
                1L,
                10L,
                new CreateMeetingParticipationRequest(null)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Host cannot apply to own meeting.");
    }

    @Test
    void applyParticipationRejectsDuplicate() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);

        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(memberRepository.findById(2L)).willReturn(Optional.of(applicant));
        given(participationRepository.findByMeetingIdAndMemberIdForUpdate(10L, 2L))
                .willReturn(Optional.of(participation(100L, meeting, applicant)));

        assertThatThrownBy(() -> participationService.applyParticipation(
                2L,
                10L,
                new CreateMeetingParticipationRequest(null)
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Participation already exists.");
    }

    @Test
    void applyParticipationConvertsUniqueConstraintRaceToConflict() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);

        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(memberRepository.findById(2L)).willReturn(Optional.of(applicant));
        given(participationRepository.findByMeetingIdAndMemberIdForUpdate(10L, 2L)).willReturn(Optional.empty());
        given(participationRepository.saveAndFlush(any(MeetingParticipation.class)))
                .willThrow(new DataIntegrityViolationException("duplicate participation"));

        assertThatThrownBy(() -> participationService.applyParticipation(
                2L,
                10L,
                new CreateMeetingParticipationRequest(null)
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Participation already exists.");
    }

    @Test
    void applyParticipationReappliesCanceledParticipationWithLockingLookup() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);
        MeetingParticipation participation = participation(100L, meeting, applicant);
        participation.cancel();

        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(memberRepository.findById(2L)).willReturn(Optional.of(applicant));
        given(participationRepository.findByMeetingIdAndMemberIdForUpdate(10L, 2L))
                .willReturn(Optional.of(participation));

        MeetingParticipationResponse response = participationService.applyParticipation(
                2L,
                10L,
                new CreateMeetingParticipationRequest("Apply again")
        );

        assertThat(response.status()).isEqualTo(ParticipationStatus.PENDING);
        assertThat(response.message()).isEqualTo("Apply again");
    }

    @Test
    void getMeetingParticipationsReturnsStatusFilteredPageForHost() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);
        MeetingParticipation participation = participation(100L, meeting, applicant);

        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));
        given(participationRepository.findByMeetingIdAndStatus(
                10L,
                ParticipationStatus.PENDING,
                PageRequest.of(0, 20)
        )).willReturn(new PageImpl<>(List.of(participation), PageRequest.of(0, 20), 1));

        PageResponse<MeetingParticipationResponse> response = participationService.getMeetingParticipations(
                1L,
                10L,
                "pending",
                PageRequest.of(0, 20)
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).extracting(MeetingParticipationResponse::id)
                .containsExactly(100L);
    }

    @Test
    void getMeetingParticipationsRejectsNonHost() {
        Member host = member(1L, "host@meetple.com", "host");
        Meeting meeting = meeting(10L, host);
        given(meetingRepository.findById(10L)).willReturn(Optional.of(meeting));

        assertThatThrownBy(() -> participationService.getMeetingParticipations(
                2L,
                10L,
                null,
                PageRequest.of(0, 20)
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only meeting host can manage participation requests.");
    }

    @Test
    void approveParticipationApprovesAndIncreasesCurrentPeople() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);
        MeetingParticipation participation = participation(100L, meeting, applicant);

        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(participationRepository.findByIdAndMeetingIdForUpdate(100L, 10L))
                .willReturn(Optional.of(participation));

        MeetingParticipationResponse response = participationService.approveParticipation(1L, 10L, 100L);

        assertThat(response.status()).isEqualTo(ParticipationStatus.APPROVED);
        assertThat(meeting.getCurrentPeople()).isEqualTo(2);
        assertThat(participation.getReviewedAt()).isNotNull();
    }

    @Test
    void approveParticipationRejectsFullMeeting() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host, 1);
        MeetingParticipation participation = participation(100L, meeting, applicant);

        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(participationRepository.findByIdAndMeetingIdForUpdate(100L, 10L))
                .willReturn(Optional.of(participation));

        assertThatThrownBy(() -> participationService.approveParticipation(1L, 10L, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Meeting capacity is full.");
    }

    @Test
    void rejectParticipationRejectsPendingParticipation() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);
        MeetingParticipation participation = participation(100L, meeting, applicant);

        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(participationRepository.findByIdAndMeetingIdForUpdate(100L, 10L))
                .willReturn(Optional.of(participation));

        MeetingParticipationResponse response = participationService.rejectParticipation(1L, 10L, 100L);

        assertThat(response.status()).isEqualTo(ParticipationStatus.REJECTED);
        assertThat(participation.getReviewedAt()).isNotNull();
    }

    @Test
    void cancelApprovedParticipationDecreasesCurrentPeople() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);
        MeetingParticipation participation = participation(100L, meeting, applicant);
        participation.approve();
        meeting.increaseCurrentPeople();

        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(participationRepository.findByIdAndMeetingIdForUpdate(100L, 10L))
                .willReturn(Optional.of(participation));

        MeetingParticipationResponse response = participationService.cancelParticipation(2L, 10L, 100L);

        assertThat(response.status()).isEqualTo(ParticipationStatus.CANCELED);
        assertThat(meeting.getCurrentPeople()).isEqualTo(1);
        assertThat(participation.getCanceledAt()).isNotNull();
    }

    @Test
    void cancelParticipationRejectsDifferentMember() {
        Member host = member(1L, "host@meetple.com", "host");
        Member applicant = member(2L, "runner@meetple.com", "runner");
        Meeting meeting = meeting(10L, host);
        MeetingParticipation participation = participation(100L, meeting, applicant);

        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(participationRepository.findByIdAndMeetingIdForUpdate(100L, 10L))
                .willReturn(Optional.of(participation));

        assertThatThrownBy(() -> participationService.cancelParticipation(3L, 10L, 100L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only applicant can cancel participation.");
    }

    private MeetingParticipation participation(Long id, Meeting meeting, Member applicant) {
        MeetingParticipation participation = MeetingParticipation.apply(meeting, applicant, "I want to join.");
        ReflectionTestUtils.setField(participation, "id", id);
        return participation;
    }

    private Meeting meeting(Long id, Member host) {
        return meeting(id, host, 10);
    }

    private Meeting meeting(Long id, Member host, int capacity) {
        Meeting meeting = Meeting.create(
                host,
                category(1L, "exercise"),
                "Weekend running",
                "Run together at an easy pace.",
                "Yeouido Park",
                "330 Yeouidong-ro, Yeongdeungpo-gu, Seoul",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500"),
                capacity,
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
