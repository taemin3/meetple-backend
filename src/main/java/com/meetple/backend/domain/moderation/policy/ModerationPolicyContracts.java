package com.meetple.backend.domain.moderation.policy;

import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class ModerationPolicyContracts {
    public static final int EMBEDDING_DIMENSIONS = 1536;

    private ModerationPolicyContracts() {
    }

    public record SearchRequest(
            @NotBlank @Size(max = 200) String keyword,
            @NotNull ReportTargetType targetType,
            ModerationPolicyType policyType,
            @NotEmpty @Size(min = EMBEDDING_DIMENSIONS, max = EMBEDDING_DIMENSIONS)
            List<@NotNull Double> queryEmbedding,
            @NotBlank @Size(max = 100) String queryEmbeddingModel,
            @NotNull @Min(1) @Max(20) Integer limit
    ) {
    }

    public record Candidate(
            long policyId,
            long policyChunkId,
            String policyCode,
            String policyTitle,
            ModerationPolicyType policyType,
            ModerationPolicyTargetType targetType,
            int policyVersion,
            String clauseCode,
            String content,
            String contentHash,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean keywordMatched,
            double semanticDistance,
            double hybridScore
    ) {
    }

    public record Candidates(List<Candidate> items, boolean hasMore) {
    }

    public record EmbeddingJob(
            long policyId,
            long policyChunkId,
            String policyCode,
            int policyVersion,
            String clauseCode,
            String content,
            String contentHash
    ) {
    }

    public record EmbeddingJobs(List<EmbeddingJob> items) {
    }

    public record EmbeddingUpsertRequest(
            @NotBlank @Size(max = 100) String embeddingModel,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String contentHash,
            @NotEmpty @Size(min = EMBEDDING_DIMENSIONS, max = EMBEDDING_DIMENSIONS)
            List<@NotNull Double> embedding
    ) {
    }
}
