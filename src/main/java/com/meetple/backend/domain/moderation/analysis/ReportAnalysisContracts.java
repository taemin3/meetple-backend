package com.meetple.backend.domain.moderation.analysis;

import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.Candidate;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class ReportAnalysisContracts {
    private ReportAnalysisContracts() {
    }

    public enum AnalysisStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED_RETRYABLE,
        FAILED_PERMANENT
    }

    public enum ModerationReportType {
        SPAM,
        ABUSE_OR_HARASSMENT,
        INAPPROPRIATE_CONTENT,
        FRAUD_OR_FALSE_INFORMATION,
        SAFETY,
        OTHER
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum ModerationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum RecommendedAction {
        DISMISS,
        WARNING,
        SUSPEND_1_DAY,
        SUSPEND_3_DAYS,
        SUSPEND_7_DAYS,
        PERMANENT_SUSPENSION,
        FORCE_DELETE_MEETING,
        MANUAL_REVIEW
    }

    public record Evidence(
            long evidenceId,
            ReportTargetType evidenceType,
            String content
    ) {
    }

    public record Context(
            long reportId,
            ReportTargetType targetType,
            ReportReason reason,
            String description,
            List<Evidence> evidence
    ) {
    }

    public record AuditedPolicySearchRequest(
            @NotNull @Min(1) Long reportId,
            @NotBlank @Size(max = 200) String keyword,
            @NotNull ReportTargetType targetType,
            ModerationPolicyType policyType,
            @NotEmpty @Size(
                    min = ModerationPolicyContracts.EMBEDDING_DIMENSIONS,
                    max = ModerationPolicyContracts.EMBEDDING_DIMENSIONS
            ) List<@NotNull Double> queryEmbedding,
            @NotBlank @Size(max = 100) String queryEmbeddingModel,
            @NotNull @Min(1) @Max(20) Integer limit
    ) {
    }

    public record AuditedPolicyCandidates(
            long retrievalId,
            List<Candidate> items,
            boolean hasMore
    ) {
    }

    public record CompleteRequest(
            @NotNull @Min(1) Long policyRetrievalId,
            @NotNull ModerationReportType reportType,
            @NotNull RiskLevel riskLevel,
            @NotNull ModerationPriority priority,
            @NotBlank @Size(max = 500) String summary,
            @NotBlank @Size(max = 1000) String rationale,
            @NotEmpty @Size(max = 10) List<@NotNull @Min(1) Long> evidenceIds,
            @NotEmpty @Size(max = 10) List<@NotNull @Min(1) Long> policyIds,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            @NotNull RecommendedAction recommendedAction
    ) {
    }

    public record Completion(
            long reportId,
            AnalysisStatus status,
            boolean idempotent
    ) {
    }

    public record FailureRequest(
            boolean retryable,
            @NotBlank @Size(max = 100)
            @Pattern(regexp = "[A-Z][A-Z0-9_]{0,99}") String failureCode
    ) {
    }
}
