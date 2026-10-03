package com.meetple.backend.domain.moderation.admin;

import static com.meetple.backend.domain.moderation.admin.AdminModerationContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.moderation.entity.Report;
import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportReviewStatus;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.repository.ReportRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.websocket.ChatAccessRevocationReason;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import com.meetple.backend.global.websocket.ChatSessionInvalidationTarget;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminModerationServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-02T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 9, 0);

    @Mock ReportRepository reportRepository;
    @Mock MemberRepository memberRepository;
    @Mock ModerationActionRepository actionRepository;
    @Mock AdminModerationQueryRepository queryRepository;
    @Mock NotificationService notificationService;
    @Mock ApplicationEventPublisher eventPublisher;

    private AdminModerationService service;
    private Member administrator;

    @BeforeEach
    void setUp() {
        service = new AdminModerationService(
                reportRepository,
                memberRepository,
                actionRepository,
                queryRepository,
                notificationService,
                eventPublisher,
                CLOCK
        );
        administrator = member(1L, MemberRole.ADMIN);
        given(memberRepository.findById(1L)).willReturn(Optional.of(administrator));
    }

    @Test
    void temporarySuspensionResolvesReportAndRevokesAllSessions() {
        Report report = report(10L, ReportTargetType.MEMBER, 2L);
        Member target = member(2L, MemberRole.USER);
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));
        given(queryRepository.findTargetMemberId(10L)).willReturn(Optional.of(2L));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(target));
        given(actionRepository.saveAndFlush(any(ModerationAction.class))).willAnswer(invocation -> {
            ModerationAction action = invocation.getArgument(0);
            ReflectionTestUtils.setField(action, "id", 100L);
            ReflectionTestUtils.setField(action, "createdAt", NOW);
            return action;
        });

        ActionResult result = service.applyAction(
                1L,
                10L,
                new ActionRequest(AdminModerationActionType.SUSPEND_3_DAYS, "반복적인 괴롭힘 확인")
        );

        assertThat(result.actionId()).isEqualTo(100L);
        assertThat(result.reviewStatus()).isEqualTo(ReportReviewStatus.RESOLVED);
        assertThat(result.effectiveUntil()).isEqualTo(NOW.plusDays(3));
        assertThat(target.getSuspendedUntil()).isEqualTo(NOW.plusDays(3));
        assertThat(report.getResolutionAction()).isEqualTo(AdminModerationActionType.SUSPEND_3_DAYS);
        verify(eventPublisher).publishEvent(any(ChatSessionInvalidationEvent.class));
    }

    @Test
    void duplicateInitialDecisionIsRejected() {
        Report report = report(10L, ReportTargetType.MEMBER, 2L);
        report.resolve(AdminModerationActionType.WARNING, 1L, NOW.minusMinutes(1));
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> service.applyAction(
                1L,
                10L,
                new ActionRequest(AdminModerationActionType.DISMISS, "이미 처리됨")
        )).isInstanceOf(ConflictException.class);

        verify(actionRepository, never()).saveAndFlush(any());
    }

    @Test
    void suspensionReleaseRequiresPreviousSuspensionFromSameReport() {
        Report report = report(10L, ReportTargetType.MEMBER, 2L);
        report.resolve(AdminModerationActionType.WARNING, 1L, NOW.minusDays(1));
        Member target = member(2L, MemberRole.USER);
        target.suspendUntil(9L, NOW.plusDays(1));
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));
        given(queryRepository.findTargetMemberId(10L)).willReturn(Optional.of(2L));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(target));
        given(actionRepository.existsByReportIdAndActionTypeAndTargetMemberId(
                10L, AdminModerationActionType.SUSPEND_1_DAY, 2L
        )).willReturn(true);

        assertThatThrownBy(() -> service.applyAction(
                1L,
                10L,
                new ActionRequest(AdminModerationActionType.RELEASE_SUSPENSION, "재검토 결과 해제")
        )).isInstanceOf(ConflictException.class);

        assertThat(target.isSuspendedAt(NOW)).isTrue();
    }

    @Test
    void meetingRestoreRequiresForceDeleteHistoryFromSameReport() {
        Report report = report(10L, ReportTargetType.MEETING, 20L);
        report.resolve(AdminModerationActionType.FORCE_DELETE_MEETING, 1L, NOW.minusDays(1));
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));
        given(queryRepository.lockMeeting(20L)).willReturn(Optional.of(
                new AdminModerationQueryRepository.MeetingModerationState(NOW.minusDays(1), 9L)
        ));
        assertThatThrownBy(() -> service.applyAction(
                1L,
                10L,
                new ActionRequest(AdminModerationActionType.RESTORE_MEETING, "복구 근거 부족")
        )).isInstanceOf(ConflictException.class);

        verify(queryRepository, never()).restoreMeeting(any(Long.class), any());
    }

    @Test
    void forceDeleteMeetingInvalidatesActiveChatSessions() {
        Report report = report(10L, ReportTargetType.MEETING, 20L);
        Member host = member(2L, MemberRole.USER);
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));
        given(queryRepository.lockMeeting(20L)).willReturn(Optional.of(
                new AdminModerationQueryRepository.MeetingModerationState(null, null)
        ));
        given(queryRepository.findTargetMemberId(10L)).willReturn(Optional.of(2L));
        given(memberRepository.findById(2L)).willReturn(Optional.of(host));
        stubSavedAction();

        service.applyAction(
                1L,
                10L,
                new ActionRequest(AdminModerationActionType.FORCE_DELETE_MEETING, "정책 위반 모임 삭제")
        );

        ArgumentCaptor<ChatSessionInvalidationEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatSessionInvalidationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().target()).isEqualTo(ChatSessionInvalidationTarget.ROOM);
        assertThat(eventCaptor.getValue().roomId()).isEqualTo(20L);
        assertThat(eventCaptor.getValue().reason()).isEqualTo(ChatAccessRevocationReason.MEETING_CANCELED);
    }

    @Test
    void forceDeleteMeetingCanSuspendHostInSameDecision() {
        Report report = report(10L, ReportTargetType.MEETING, 20L);
        Member host = member(2L, MemberRole.USER);
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));
        given(queryRepository.findTargetMemberId(10L)).willReturn(Optional.of(2L));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(host));
        given(memberRepository.findById(2L)).willReturn(Optional.of(host));
        given(queryRepository.lockMeeting(20L)).willReturn(Optional.of(
                new AdminModerationQueryRepository.MeetingModerationState(null, null)
        ));
        given(actionRepository.saveAndFlush(any(ModerationAction.class))).willAnswer(invocation -> {
            ModerationAction saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id",
                    saved.getActionType() == AdminModerationActionType.FORCE_DELETE_MEETING ? 100L : 101L);
            ReflectionTestUtils.setField(saved, "createdAt", NOW);
            return saved;
        });

        ActionResult result = service.applyAction(
                1L,
                10L,
                new ActionRequest(
                        AdminModerationActionType.FORCE_DELETE_MEETING,
                        AdminModerationActionType.SUSPEND_3_DAYS,
                        "허위 비용 안내 확인"
                )
        );

        assertThat(result.reviewStatus()).isEqualTo(ReportReviewStatus.RESOLVED);
        assertThat(result.action()).isEqualTo(AdminModerationActionType.FORCE_DELETE_MEETING);
        assertThat(result.additionalAction()).isEqualTo(AdminModerationActionType.SUSPEND_3_DAYS);
        assertThat(result.additionalActionId()).isEqualTo(101L);
        assertThat(result.targetMeetingId()).isEqualTo(20L);
        assertThat(result.targetMemberId()).isEqualTo(2L);
        assertThat(result.effectiveUntil()).isEqualTo(NOW.plusDays(3));
        assertThat(report.getResolutionAction()).isEqualTo(AdminModerationActionType.FORCE_DELETE_MEETING);
        assertThat(host.getSuspendedUntil()).isEqualTo(NOW.plusDays(3));
        verify(queryRepository).updateMeetingDeletion(20L, NOW, 10L);
        verify(eventPublisher, times(2)).publishEvent(any(ChatSessionInvalidationEvent.class));

        ArgumentCaptor<ModerationAction> actionCaptor = ArgumentCaptor.forClass(ModerationAction.class);
        verify(actionRepository, times(2)).saveAndFlush(actionCaptor.capture());
        assertThat(actionCaptor.getAllValues())
                .extracting(ModerationAction::getActionType)
                .containsExactly(
                        AdminModerationActionType.FORCE_DELETE_MEETING,
                        AdminModerationActionType.SUSPEND_3_DAYS
                );
    }

    @Test
    void additionalSuspensionIsRejectedWithoutMeetingForceDelete() {
        Report report = report(10L, ReportTargetType.MEMBER, 2L);
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> service.applyAction(
                1L,
                10L,
                new ActionRequest(
                        AdminModerationActionType.WARNING,
                        AdminModerationActionType.SUSPEND_3_DAYS,
                        "잘못된 복합 처리"
                )
        )).isInstanceOf(BadRequestException.class)
                .hasMessage("모임 강제 삭제에는 모임장 정지만 추가할 수 있습니다.");

        verify(actionRepository, never()).saveAndFlush(any());
    }

    @Test
    void restoreMeetingUsesCurrentTimeToCompleteEndedMeetingAtomically() {
        Report report = report(10L, ReportTargetType.MEETING, 20L);
        report.resolve(AdminModerationActionType.FORCE_DELETE_MEETING, 1L, NOW.minusDays(1));
        Member host = member(2L, MemberRole.USER);
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));
        given(queryRepository.lockMeeting(20L)).willReturn(Optional.of(
                new AdminModerationQueryRepository.MeetingModerationState(NOW.minusDays(1), 10L)
        ));
        given(actionRepository.existsByReportIdAndActionTypeAndTargetMeetingId(
                10L, AdminModerationActionType.FORCE_DELETE_MEETING, 20L
        )).willReturn(true);
        given(queryRepository.findTargetMemberId(10L)).willReturn(Optional.of(2L));
        given(memberRepository.findById(2L)).willReturn(Optional.of(host));
        stubSavedAction();

        service.applyAction(
                1L,
                10L,
                new ActionRequest(AdminModerationActionType.RESTORE_MEETING, "오판 확인 후 복구")
        );

        verify(queryRepository).restoreMeeting(20L, NOW);
    }

    @Test
    void normalMemberCannotApproveModerationAction() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, MemberRole.USER)));

        assertThatThrownBy(() -> service.applyAction(
                1L,
                10L,
                new ActionRequest(AdminModerationActionType.DISMISS, "기각")
        )).isInstanceOf(ForbiddenException.class);

        verify(reportRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void administratorAccountCannotBeSuspendedThroughReportAction() {
        Report report = report(10L, ReportTargetType.MEMBER, 2L);
        Member targetAdmin = member(2L, MemberRole.ADMIN);
        given(reportRepository.findByIdForUpdate(10L)).willReturn(Optional.of(report));
        given(queryRepository.findTargetMemberId(10L)).willReturn(Optional.of(2L));
        given(memberRepository.findByIdForUpdate(2L)).willReturn(Optional.of(targetAdmin));

        assertThatThrownBy(() -> service.applyAction(
                1L,
                10L,
                new ActionRequest(AdminModerationActionType.PERMANENT_SUSPENSION, "잘못된 대상")
        )).isInstanceOf(ConflictException.class)
                .hasMessage("관리자 계정에는 신고 제재를 적용할 수 없습니다.");
    }

    private static Member member(long id, MemberRole role) {
        Member member = Member.createUser("member" + id + "@meetple.com", "password", "member" + id, null);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "role", role);
        return member;
    }

    private static Report report(long id, ReportTargetType targetType, long targetId) {
        Report report = Report.create(
                member(99L, MemberRole.USER),
                targetType,
                targetId,
                ReportReason.ABUSE_OR_HARASSMENT,
                null
        );
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }

    private void stubSavedAction() {
        given(actionRepository.saveAndFlush(any(ModerationAction.class))).willAnswer(invocation -> {
            ModerationAction action = invocation.getArgument(0);
            ReflectionTestUtils.setField(action, "id", 100L);
            ReflectionTestUtils.setField(action, "createdAt", NOW);
            return action;
        });
    }
}
