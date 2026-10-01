package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;

import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.Candidate;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ReportAnalysisRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ReportAnalysisRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initialize(
            long reportId,
            ReportTargetType evidenceType,
            long sourceId,
            String content,
            String contentHash
    ) {
        jdbc.update("""
                INSERT INTO report_analysis_evidence
                    (report_id, evidence_type, source_id, content, content_hash)
                VALUES (:reportId, :evidenceType, :sourceId, :content, :contentHash)
                """, Map.of(
                "reportId", reportId,
                "evidenceType", evidenceType.name(),
                "sourceId", sourceId,
                "content", content,
                "contentHash", contentHash
        ));
        jdbc.update("""
                INSERT INTO report_analyses (report_id, status)
                VALUES (:reportId, 'PENDING')
                """, Map.of("reportId", reportId));
    }

    public Optional<Context> findContext(long reportId) {
        List<ContextHeader> headers = jdbc.query("""
                SELECT id, target_type, reason, other_description
                FROM reports
                WHERE id = :reportId
                """, Map.of("reportId", reportId), (rs, rowNum) -> new ContextHeader(
                rs.getLong("id"),
                ReportTargetType.valueOf(rs.getString("target_type")),
                ReportReason.valueOf(rs.getString("reason")),
                rs.getString("other_description")
        ));
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        ContextHeader header = headers.getFirst();
        List<Evidence> evidence = jdbc.query("""
                SELECT id, evidence_type, content
                FROM report_analysis_evidence
                WHERE report_id = :reportId
                ORDER BY id
                """, Map.of("reportId", reportId), (rs, rowNum) -> new Evidence(
                rs.getLong("id"),
                ReportTargetType.valueOf(rs.getString("evidence_type")),
                rs.getString("content")
        ));
        return Optional.of(new Context(
                header.reportId(),
                header.targetType(),
                header.reason(),
                header.description(),
                evidence
        ));
    }

    public void markProcessing(long reportId) {
        jdbc.update("""
                UPDATE report_analyses
                SET status = 'PROCESSING',
                    attempt_count = attempt_count + 1,
                    started_at = COALESCE(started_at, CURRENT_TIMESTAMP),
                    failure_code = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE report_id = :reportId
                  AND status IN ('PENDING', 'PROCESSING', 'FAILED_RETRYABLE')
                """, Map.of("reportId", reportId));
    }

    public Optional<ReportTargetType> findTargetType(long reportId) {
        List<ReportTargetType> rows = jdbc.query("""
                SELECT target_type
                FROM reports
                WHERE id = :reportId
                """, Map.of("reportId", reportId), (rs, rowNum) ->
                ReportTargetType.valueOf(rs.getString("target_type")));
        return rows.stream().findFirst();
    }

    public long recordRetrieval(
            long reportId,
            String embeddingModel,
            String keyword,
            ModerationPolicyType policyType,
            List<Candidate> candidates
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("reportId", reportId)
                .addValue("embeddingModel", embeddingModel)
                .addValue("keyword", keyword)
                .addValue("policyType", policyType == null ? null : policyType.name());
        jdbc.update("""
                INSERT INTO moderation_policy_retrievals
                    (report_id, query_embedding_model, keyword, requested_policy_type)
                VALUES (:reportId, :embeddingModel, :keyword, :policyType)
                """, parameters, keyHolder, new String[]{"id"});
        long retrievalId = keyHolder.getKeyAs(Long.class);
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            jdbc.update("""
                    INSERT INTO moderation_policy_retrieval_items
                        (retrieval_id, policy_id, policy_chunk_id, result_rank, content_hash)
                    VALUES (:retrievalId, :policyId, :policyChunkId, :resultRank, :contentHash)
                    """, Map.of(
                    "retrievalId", retrievalId,
                    "policyId", candidate.policyId(),
                    "policyChunkId", candidate.policyChunkId(),
                    "resultRank", index + 1,
                    "contentHash", candidate.contentHash()
            ));
        }
        return retrievalId;
    }

    public Optional<AnalysisLock> lockAnalysis(long reportId) {
        List<AnalysisLock> rows = jdbc.query("""
                SELECT analysis.status, analysis.result_hash, report.target_type
                FROM report_analyses analysis
                JOIN reports report ON report.id = analysis.report_id
                WHERE analysis.report_id = :reportId
                FOR UPDATE OF analysis
                """, Map.of("reportId", reportId), (rs, rowNum) -> new AnalysisLock(
                AnalysisStatus.valueOf(rs.getString("status")),
                rs.getString("result_hash"),
                ReportTargetType.valueOf(rs.getString("target_type"))
        ));
        return rows.stream().findFirst();
    }

    public boolean retrievalBelongsToReport(long reportId, long retrievalId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM moderation_policy_retrievals
                WHERE id = :retrievalId
                  AND report_id = :reportId
                """, Map.of("reportId", reportId, "retrievalId", retrievalId), Integer.class);
        return count != null && count == 1;
    }

    public Set<Long> findEvidenceIds(long reportId, List<Long> evidenceIds) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT id
                FROM report_analysis_evidence
                WHERE report_id = :reportId
                  AND id IN (:evidenceIds)
                """, Map.of("reportId", reportId, "evidenceIds", evidenceIds), Long.class));
    }

    public Set<Long> findRetrievedPolicyIds(long retrievalId, List<Long> policyIds) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT policy_id
                FROM moderation_policy_retrieval_items
                WHERE retrieval_id = :retrievalId
                  AND policy_id IN (:policyIds)
                """, Map.of("retrievalId", retrievalId, "policyIds", policyIds), Long.class));
    }

    public void complete(
            long reportId,
            CompleteRequest request,
            String resultHash,
            List<Long> evidenceIds,
            List<Long> policyIds
    ) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("reportId", reportId);
        parameters.put("policyRetrievalId", request.policyRetrievalId());
        parameters.put("reportType", request.reportType().name());
        parameters.put("riskLevel", request.riskLevel().name());
        parameters.put("priority", request.priority().name());
        parameters.put("summary", request.summary().strip());
        parameters.put("rationale", request.rationale().strip());
        parameters.put("confidence", request.confidence());
        parameters.put("recommendedAction", request.recommendedAction().name());
        parameters.put("resultHash", resultHash);
        jdbc.update("""
                UPDATE report_analyses
                SET status = 'COMPLETED',
                    report_type = :reportType,
                    risk_level = :riskLevel,
                    priority = :priority,
                    summary = :summary,
                    rationale = :rationale,
                    confidence = :confidence,
                    recommended_action = :recommendedAction,
                    policy_retrieval_id = :policyRetrievalId,
                    result_hash = :resultHash,
                    failure_code = NULL,
                    completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE report_id = :reportId
                """, parameters);
        insertSelections("report_analysis_evidence_selections", "evidence_id", reportId, evidenceIds);
        insertSelections("report_analysis_policy_selections", "policy_id", reportId, policyIds);
    }

    public void fail(long reportId, boolean retryable, String failureCode) {
        jdbc.update("""
                UPDATE report_analyses
                SET status = :status,
                    failure_code = :failureCode,
                    completed_at = CASE WHEN :retryable THEN NULL ELSE CURRENT_TIMESTAMP END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE report_id = :reportId
                """, Map.of(
                "reportId", reportId,
                "status", retryable ? "FAILED_RETRYABLE" : "FAILED_PERMANENT",
                "retryable", retryable,
                "failureCode", failureCode
        ));
    }

    private void insertSelections(
            String table,
            String idColumn,
            long reportId,
            List<Long> selectedIds
    ) {
        String sql = "INSERT INTO " + table + " (report_id, " + idColumn + ") "
                + "VALUES (:reportId, :selectedId)";
        selectedIds.forEach(selectedId -> jdbc.update(
                sql,
                Map.of("reportId", reportId, "selectedId", selectedId)
        ));
    }

    public record AnalysisLock(
            AnalysisStatus status,
            String resultHash,
            ReportTargetType targetType
    ) {
    }

    private record ContextHeader(
            long reportId,
            ReportTargetType targetType,
            ReportReason reason,
            String description
    ) {
    }
}
