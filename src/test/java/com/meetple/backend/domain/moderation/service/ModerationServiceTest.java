package com.meetple.backend.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import com.meetple.backend.domain.chat.repository.ChatMessageRepository;
import com.meetple.backend.domain.chat.service.ChatAccessPolicy;
import com.meetple.backend.domain.chat.entity.ChatMessage;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.moderation.dto.request.CreateReportRequest;
import com.meetple.backend.domain.moderation.entity.Report;
import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.repository.ReportRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock MeetingRepository meetingRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ChatAccessPolicy chatAccessPolicy;
    @Mock ReportRepository reportRepository;

    private ModerationService service;

    @BeforeEach
    void setUp() {
        service = new ModerationService(memberRepository, meetingRepository, chatMessageRepository,
                chatAccessPolicy, reportRepository);
    }

    @Test
    void createsMemberReportAfterResolvingTargetOnServer() {
        Member reporter = member(1L, "reporter");
        Member target = member(2L, "target");
        given(memberRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(memberRepository.findById(2L)).willReturn(Optional.of(target));
        given(reportRepository.save(org.mockito.ArgumentMatchers.any(Report.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.createReport(1L, new CreateReportRequest(
                ReportTargetType.MEMBER, 2L, ReportReason.SPAM, null
        ));

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getReporter()).isSameAs(reporter);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getTargetId()).isEqualTo(2L);
    }

    @Test
    void rejectsSelfReport() {
        Member reporter = member(1L, "reporter");
        given(memberRepository.findById(1L)).willReturn(Optional.of(reporter));

        assertThatThrownBy(() -> service.createReport(1L, new CreateReportRequest(
                ReportTargetType.MEMBER, 1L, ReportReason.SPAM, null
        ))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsMissingTarget() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, "reporter")));
        given(memberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReport(1L, new CreateReportRequest(
                ReportTargetType.MEMBER, 999L, ReportReason.SPAM, null
        ))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void requiresDescriptionForOtherReason() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, "reporter")));
        given(memberRepository.findById(2L)).willReturn(Optional.of(member(2L, "target")));

        assertThatThrownBy(() -> service.createReport(1L, new CreateReportRequest(
                ReportTargetType.MEMBER, 2L, ReportReason.OTHER, " "
        ))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifiesChatRoomAccessBeforeCreatingMessageReport() {
        Member reporter = member(1L, "reporter");
        Member sender = member(2L, "sender");
        ChatMessage message = mock(ChatMessage.class);
        Meeting meeting = mock(Meeting.class);
        given(memberRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(chatMessageRepository.findById(100L)).willReturn(Optional.of(message));
        given(message.getMeeting()).willReturn(meeting);
        given(meeting.getId()).willReturn(10L);
        given(message.getSender()).willReturn(sender);
        given(chatAccessPolicy.getAccessibleMeeting(1L, 10L)).willReturn(meeting);
        given(reportRepository.save(org.mockito.ArgumentMatchers.any(Report.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        service.createReport(1L, new CreateReportRequest(
                ReportTargetType.CHAT_MESSAGE, 100L, ReportReason.SPAM, null
        ));

        verify(chatAccessPolicy).getAccessibleMeeting(1L, 10L);
        verify(reportRepository).save(org.mockito.ArgumentMatchers.any(Report.class));
    }

    private Member member(Long id, String nickname) {
        Member member = Member.createUser(nickname + "@example.com", "password", nickname, null);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
