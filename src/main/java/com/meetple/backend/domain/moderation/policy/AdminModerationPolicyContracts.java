package com.meetple.backend.domain.moderation.policy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminModerationPolicyContracts {
    private AdminModerationPolicyContracts() {
    }

    public record ClauseRequest(
            @NotBlank
            @Size(max = 100)
            @Pattern(regexp = "[A-Z][A-Z0-9_-]{0,99}") String clauseCode,
            @NotBlank @Size(max = 4000) String content
    ) {
    }

    public record CreatePolicyRequest(
            @NotBlank
            @Size(max = 100)
            @Pattern(regexp = "[A-Z][A-Z0-9_-]{2,99}") String policyCode,
            @NotBlank @Size(max = 200) String title,
            @NotNull ModerationPolicyType policyType,
            @NotNull ModerationPolicyTargetType targetType,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotEmpty @Size(max = 50) List<@Valid ClauseRequest> clauses
    ) {
    }

    public record CreateVersionRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull ModerationPolicyType policyType,
            @NotNull ModerationPolicyTargetType targetType,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotEmpty @Size(max = 50) List<@Valid ClauseRequest> clauses
    ) {
    }

    public record ActivationRequest(@NotNull Boolean active) {
    }

    public record PolicySummary(
            long policyId,
            String policyCode,
            String title,
            ModerationPolicyType policyType,
            ModerationPolicyTargetType targetType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            int version,
            int clauseCount,
            int missingEmbeddingCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record PolicyClause(
            long policyChunkId,
            String clauseCode,
            int chunkOrder,
            String content,
            String contentHash,
            boolean embedded
    ) {
    }

    public enum PolicyAuditAction {
        CREATED,
        VERSION_CREATED,
        ACTIVATED,
        DEACTIVATED
    }

    public record PolicyAudit(
            long auditId,
            long administratorMemberId,
            String administratorNickname,
            PolicyAuditAction action,
            LocalDateTime createdAt
    ) {
    }

    public record PolicyDetail(
            long policyId,
            String policyCode,
            String title,
            ModerationPolicyType policyType,
            ModerationPolicyTargetType targetType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            int version,
            String embeddingModel,
            int missingEmbeddingCount,
            List<PolicyClause> clauses,
            List<PolicyAudit> audits,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
