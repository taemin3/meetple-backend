package com.meetple.backend.domain.moderation.admin;

import static com.meetple.backend.domain.moderation.admin.AdminModerationContracts.*;
import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.AnalysisStatus;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.moderation.entity.Report;
import com.meetple.backend.domain.moderation.entity.ReportReviewStatus;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.repository.ReportRepository;
import com.meetple.backend.domain.notification.service.NotificationService;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ConflictException;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import com.meetple.backend.global.websocket.ChatSessionInvalidationEvent;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminModerationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt");
    private static final String MEMBER_NOT_FOUND_MESSAGE = "제재 대상 회원을 찾을 수 없습니다.";
    private static final String ACTION_CONFLICT_MESSAGE = "현재 신고 또는 제재 상태에서는 요청한 처리를 할 수 없습니다.";

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final ModerationActionRepository actionRepository;
    private final AdminModerationQueryRepository queryRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminModerationService(
            ReportRepository reportRepository,
            MemberRepository memberRepository,
            ModerationActionRepository actionRepository,
            AdminModerationQueryRepository queryRepository,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher
    ) {
        this(
                reportRepository,
                memberRepository,
                actionRepository,
                queryRepository,
                notificationService,
                eventPublisher,
                Clock.system(ZoneId.of("Asia/Seoul"))
        );
    }

    AdminModerationService(
            ReportRepository reportRepository,
            MemberRepository memberRepository,
            ModerationActionRepository actionRepository,
            AdminModerationQueryRepository queryRepository,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.reportRepository = reportRepository;
        this.memberRepository = memberRepository;
        this.actionRepository = actionRepository;
        this.queryRepository = queryRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public PageResponse<ReportSummary> getReports(
            ReportReviewStatus reviewStatus,
            AnalysisStatus analysisStatus,
            Pageable pageable
    ) {
        validatePageable(pageable);
        boolean ascending = pageable.getSort().stream()
                .findFirst()
                .map(order -> order.getDirection().isAscending())
                .orElse(false);
        return PageResponse.from(queryRepository.findReports(
                reviewStatus,
                analysisStatus,
                pageable,
                ascending
        ));
    }

    public ReportDetail getReport(long reportId) {
        return queryRepository.findReport(reportId)
                .orElseThrow(() -> new NotFoundException("신고를 찾을 수 없습니다."));
    }

    @Transactional
    public ActionResult applyAction(long administratorMemberId, long reportId, ActionRequest request) {
        Member administrator = memberRepository.findById(administratorMemberId)
                .orElseThrow(() -> new ForbiddenException("관리자 계정을 확인할 수 없습니다."));
        if (administrator.getRole() != MemberRole.ADMIN) {
            throw new ForbiddenException("관리자만 신고 처리를 승인할 수 있습니다.");
        }

        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new NotFoundException("신고를 찾을 수 없습니다."));
        AdminModerationActionType action = request.action();
        validateActionPhase(report, action);
        validateAdditionalAction(report, action, request.additionalAction());

        LocalDateTime now = LocalDateTime.now(clock);
        if (request.additionalAction() != null) {
            return applyCombinedMeetingAction(
                    administratorMemberId,
                    report,
                    action,
                    request.additionalAction(),
                    request.reason().strip(),
                    now
            );
        }

        Long targetMemberId = null;
        Long targetMeetingId = null;
        LocalDateTime effectiveUntil = null;

        if (action.targetsMember()) {
            targetMemberId = queryRepository.findTargetMemberId(reportId)
                    .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
            Member targetMember = memberRepository.findByIdForUpdate(targetMemberId)
                    .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
            validateModeratableMember(targetMember);
            effectiveUntil = applyMemberAction(report, action, targetMember, now);
        } else if (action.targetsMeeting()) {
            targetMeetingId = applyMeetingAction(report, action, now);
        }

        if (action.isInitialResolution()) {
            report.resolve(action, administratorMemberId, now);
        }

        ModerationAction saved = actionRepository.saveAndFlush(ModerationAction.create(
                reportId,
                administratorMemberId,
                action,
                targetMemberId,
                targetMeetingId,
                request.reason().strip(),
                effectiveUntil
        ));
        return new ActionResult(
                saved.getId(),
                reportId,
                report.getReviewStatus(),
                action,
                targetMemberId,
                targetMeetingId,
                effectiveUntil,
                null,
                null,
                saved.getCreatedAt()
        );
    }

    private ActionResult applyCombinedMeetingAction(
            long administratorMemberId,
            Report report,
            AdminModerationActionType action,
            AdminModerationActionType additionalAction,
            String reason,
            LocalDateTime now
    ) {
        Long targetMemberId = queryRepository.findTargetMemberId(report.getId())
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
        Member targetMember = memberRepository.findByIdForUpdate(targetMemberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
        validateModeratableMember(targetMember);
        if (targetMember.isSuspendedAt(now)) {
            throw new ConflictException(ACTION_CONFLICT_MESSAGE);
        }

        Long targetMeetingId = report.getTargetId();
        AdminModerationQueryRepository.MeetingModerationState meetingState =
                queryRepository.lockMeeting(targetMeetingId)
                        .orElseThrow(() -> new NotFoundException("모임을 찾을 수 없습니다."));
        if (meetingState.deletedAt() != null) {
            throw new ConflictException(ACTION_CONFLICT_MESSAGE);
        }

        applyForceDeleteMeeting(report, targetMeetingId, now);
        LocalDateTime effectiveUntil = applyMemberAction(
                report,
                additionalAction,
                targetMember,
                now
        );
        report.resolve(action, administratorMemberId, now);

        ModerationAction primarySaved = actionRepository.saveAndFlush(ModerationAction.create(
                report.getId(),
                administratorMemberId,
                action,
                null,
                targetMeetingId,
                reason,
                null
        ));
        ModerationAction additionalSaved = actionRepository.saveAndFlush(ModerationAction.create(
                report.getId(),
                administratorMemberId,
                additionalAction,
                targetMemberId,
                null,
                reason,
                effectiveUntil
        ));
        return new ActionResult(
                primarySaved.getId(),
                report.getId(),
                report.getReviewStatus(),
                action,
                targetMemberId,
                targetMeetingId,
                effectiveUntil,
                additionalSaved.getId(),
                additionalAction,
                primarySaved.getCreatedAt()
        );
    }

    private LocalDateTime applyMemberAction(
            Report report,
            AdminModerationActionType action,
            Member targetMember,
            LocalDateTime now
    ) {
        return switch (action) {
            case WARNING -> {
                if (!queryRepository.wasAutomaticallyWarned(report.getId())) {
                    notificationService.notify(
                            targetMember,
                            "MODERATION_WARNING",
                            "운영 정책 위반 경고",
                            "운영 정책 위반 신고가 관리자 검토로 확인되어 경고가 발송되었습니다.",
                            null
                    );
                }
                yield null;
            }
            case SUSPEND_1_DAY, SUSPEND_3_DAYS, SUSPEND_7_DAYS -> {
                if (targetMember.isSuspendedAt(now)) {
                    throw new ConflictException(ACTION_CONFLICT_MESSAGE);
                }
                LocalDateTime until = targetMember.suspendUntil(
                        report.getId(),
                        now.plus(action.suspensionDuration().orElseThrow())
                );
                revokeSessions(targetMember.getId());
                notificationService.notify(
                        targetMember,
                        "MODERATION_SUSPENSION",
                        "이용 정지 안내",
                        "운영 정책 위반이 확인되어 일정 기간 서비스 이용이 제한되었습니다.",
                        null
                );
                yield until;
            }
            case PERMANENT_SUSPENSION -> {
                if (targetMember.isSuspendedAt(now)) {
                    throw new ConflictException(ACTION_CONFLICT_MESSAGE);
                }
                targetMember.suspendPermanently(report.getId(), now);
                revokeSessions(targetMember.getId());
                notificationService.notify(
                        targetMember,
                        "MODERATION_SUSPENSION",
                        "영구 이용 정지 안내",
                        "운영 정책 위반이 확인되어 서비스 이용이 영구 제한되었습니다.",
                        null
                );
                yield null;
            }
            case RELEASE_SUSPENSION -> {
                if (!hasSuspensionAction(report.getId(), targetMember.getId())
                        || !targetMember.isSuspendedAt(now)
                        || !report.getId().equals(targetMember.getSuspensionReportId())) {
                    throw new ConflictException(ACTION_CONFLICT_MESSAGE);
                }
                targetMember.releaseSuspension();
                notificationService.notify(
                        targetMember,
                        "MODERATION_SUSPENSION_RELEASED",
                        "이용 정지 해제 안내",
                        "관리자 검토로 서비스 이용 제한이 해제되었습니다.",
                        null
                );
                yield null;
            }
            default -> throw new BadRequestException("회원 대상 처리 유형이 아닙니다.");
        };
    }

    private Long applyMeetingAction(
            Report report,
            AdminModerationActionType action,
            LocalDateTime now
    ) {
        if (report.getTargetType() != ReportTargetType.MEETING) {
            throw new BadRequestException("모임 신고에만 모임 삭제 또는 복구를 적용할 수 있습니다.");
        }
        Long meetingId = report.getTargetId();
        AdminModerationQueryRepository.MeetingModerationState state = queryRepository.lockMeeting(meetingId)
                .orElseThrow(() -> new NotFoundException("모임을 찾을 수 없습니다."));
        if (action == AdminModerationActionType.FORCE_DELETE_MEETING) {
            if (state.deletedAt() != null) {
                throw new ConflictException(ACTION_CONFLICT_MESSAGE);
            }
            applyForceDeleteMeeting(report, meetingId, now);
            return meetingId;
        }
        if (action == AdminModerationActionType.RESTORE_MEETING) {
            if (state.deletedAt() == null
                    || !report.getId().equals(state.moderationReportId())
                    || !actionRepository.existsByReportIdAndActionTypeAndTargetMeetingId(
                    report.getId(), AdminModerationActionType.FORCE_DELETE_MEETING, meetingId)) {
                throw new ConflictException(ACTION_CONFLICT_MESSAGE);
            }
            queryRepository.restoreMeeting(meetingId, now);
            notifyMeetingHost(report.getId(), "모임 복구 안내",
                    "관리자 검토로 삭제된 모임이 복구되었습니다.");
            return meetingId;
        }
        throw new BadRequestException("모임 대상 처리 유형이 아닙니다.");
    }

    private void applyForceDeleteMeeting(Report report, Long meetingId, LocalDateTime now) {
        queryRepository.updateMeetingDeletion(meetingId, now, report.getId());
        eventPublisher.publishEvent(ChatSessionInvalidationEvent.meetingCanceled(meetingId));
        notifyMeetingHost(report.getId(), "모임 강제 삭제 안내",
                "운영 정책 위반이 확인되어 모임이 관리자에 의해 삭제되었습니다.");
    }

    private void notifyMeetingHost(long reportId, String title, String message) {
        Long memberId = queryRepository.findTargetMemberId(reportId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND_MESSAGE));
        notificationService.notify(member, "MODERATION_MEETING_ACTION", title, message, null);
    }

    private boolean hasSuspensionAction(long reportId, long targetMemberId) {
        return actionRepository.existsByReportIdAndActionTypeAndTargetMemberId(
                reportId, AdminModerationActionType.SUSPEND_1_DAY, targetMemberId)
                || actionRepository.existsByReportIdAndActionTypeAndTargetMemberId(
                reportId, AdminModerationActionType.SUSPEND_3_DAYS, targetMemberId)
                || actionRepository.existsByReportIdAndActionTypeAndTargetMemberId(
                reportId, AdminModerationActionType.SUSPEND_7_DAYS, targetMemberId)
                || actionRepository.existsByReportIdAndActionTypeAndTargetMemberId(
                reportId, AdminModerationActionType.PERMANENT_SUSPENSION, targetMemberId);
    }

    private void revokeSessions(Long memberId) {
        eventPublisher.publishEvent(ChatSessionInvalidationEvent.memberSuspended(memberId));
    }

    private void validateActionPhase(Report report, AdminModerationActionType action) {
        if (action.isInitialResolution() && report.getReviewStatus() != ReportReviewStatus.PENDING) {
            throw new ConflictException(ACTION_CONFLICT_MESSAGE);
        }
        if (!action.isInitialResolution() && report.getReviewStatus() != ReportReviewStatus.RESOLVED) {
            throw new ConflictException(ACTION_CONFLICT_MESSAGE);
        }
    }

    private void validateAdditionalAction(
            Report report,
            AdminModerationActionType action,
            AdminModerationActionType additionalAction
    ) {
        if (additionalAction == null) {
            return;
        }
        if (report.getTargetType() != ReportTargetType.MEETING
                || action != AdminModerationActionType.FORCE_DELETE_MEETING
                || !additionalAction.isSuspension()) {
            throw new BadRequestException("모임 강제 삭제에는 모임장 정지만 추가할 수 있습니다.");
        }
    }

    private void validateModeratableMember(Member member) {
        if (member.getRole() == MemberRole.ADMIN) {
            throw new ConflictException("관리자 계정에는 신고 제재를 적용할 수 없습니다.");
        }
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new BadRequestException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORTS.contains(order.getProperty())) {
                throw new BadRequestException("지원하지 않는 정렬 기준입니다.");
            }
        });
    }
}
