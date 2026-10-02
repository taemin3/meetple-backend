package com.meetple.backend.domain.moderation.admin;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;

import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportReviewStatus;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminModerationContracts {
    private AdminModerationContracts() {
    }

    public record ReportSummary(
            long reportId,
            ReportTargetType targetType,
            long targetId,
            Long targetMemberId,
            String targetNickname,
            ReportReason reason,
            ReportReviewStatus reviewStatus,
            AnalysisStatus analysisStatus,
            RiskLevel riskLevel,
            ModerationPriority priority,
            BigDecimal confidence,
            RecommendedAction recommendedAction,
            boolean automaticWarningIssued,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt
    ) {
    }

    public record AnalysisDetail(
            AnalysisStatus status,
            ModerationReportType reportType,
            RiskLevel riskLevel,
            ModerationPriority priority,
            String summary,
            String rationale,
            BigDecimal confidence,
            RecommendedAction recommendedAction,
            String failureCode,
            LocalDateTime completedAt
    ) {
    }

    public record PolicyBasis(
            long policyId,
            String policyCode,
            String title,
            int version
    ) {
    }

    public record ActionHistory(
            long actionId,
            long reportId,
            long administratorMemberId,
            String administratorNickname,
            AdminModerationActionType actionType,
            Long targetMemberId,
            Long targetMeetingId,
            String reason,
            LocalDateTime effectiveUntil,
            LocalDateTime createdAt
    ) {
    }

    public record WarningHistory(
            long reportId,
            long targetMemberId,
            LocalDateTime createdAt
    ) {
    }

    public record TargetState(
            Long memberId,
            LocalDateTime suspendedUntil,
            LocalDateTime permanentlySuspendedAt,
            Long suspensionReportId,
            Long meetingId,
            LocalDateTime meetingDeletedAt,
            Long meetingDeletionReportId
    ) {
    }

    public record ReportDetail(
            ReportSummary report,
            long reporterMemberId,
            String reporterNickname,
            String otherDescription,
            String evidenceContent,
            AnalysisDetail analysis,
            List<PolicyBasis> policyBasis,
            List<WarningHistory> warnings,
            List<ActionHistory> actions,
            TargetState targetState
    ) {
    }

    public record ActionRequest(
            @NotNull AdminModerationActionType action,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record ActionResult(
            long actionId,
            long reportId,
            ReportReviewStatus reviewStatus,
            AdminModerationActionType action,
            Long targetMemberId,
            Long targetMeetingId,
            LocalDateTime effectiveUntil,
            LocalDateTime createdAt
    ) {
    }
}
