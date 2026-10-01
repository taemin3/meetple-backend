package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;

import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportAnalysisRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ReportAnalysisRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initialize(long reportId, String targetSnapshot, String targetSnapshotHash) {
        int updated = jdbc.update("""
                UPDATE reports
                SET target_snapshot = :targetSnapshot,
                    target_snapshot_hash = :targetSnapshotHash
                WHERE id = :reportId
                  AND target_snapshot IS NULL
                  AND target_snapshot_hash IS NULL
                """, Map.of(
                "reportId", reportId,
                "targetSnapshot", targetSnapshot,
                "targetSnapshotHash", targetSnapshotHash
        ));
        if (updated != 1) {
            throw new IllegalStateException("신고 스냅샷을 저장할 수 없습니다.");
        }
        jdbc.update("""
                INSERT INTO report_analyses (report_id, status)
                VALUES (:reportId, 'PENDING')
                """, Map.of("reportId", reportId));
    }

    public Optional<Context> findContext(long reportId) {
        List<Context> rows = jdbc.query("""
                SELECT id, target_type, reason, other_description, target_snapshot
                FROM reports
                WHERE id = :reportId
                  AND target_snapshot IS NOT NULL
                """, Map.of("reportId", reportId), (rs, rowNum) -> {
            ReportTargetType targetType = ReportTargetType.valueOf(rs.getString("target_type"));
            return new Context(
                    rs.getLong("id"),
                    targetType,
                    ReportReason.valueOf(rs.getString("reason")),
                    rs.getString("other_description"),
                    List.of(new Evidence(
                            rs.getLong("id"),
                            targetType,
                            rs.getString("target_snapshot")
                    ))
            );
        });
        return rows.stream().findFirst();
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

    public Set<Long> findApplicablePolicyIds(
            ReportTargetType targetType,
            List<Long> policyIds,
            LocalDate effectiveDate
    ) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT id
                FROM moderation_policies
                WHERE id IN (:policyIds)
                  AND active = TRUE
                  AND effective_from <= :effectiveDate
                  AND (effective_to IS NULL OR effective_to >= :effectiveDate)
                  AND (target_type = 'ALL' OR target_type = :targetType)
                """, Map.of(
                "targetType", targetType.name(),
                "policyIds", policyIds,
                "effectiveDate", effectiveDate
        ), Long.class));
    }

    public void complete(
            long reportId,
            CompleteRequest request,
            String resultHash,
            List<Long> policyIds
    ) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("reportId", reportId);
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
                    result_hash = :resultHash,
                    failure_code = NULL,
                    completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE report_id = :reportId
                """, parameters);
        policyIds.forEach(policyId -> jdbc.update("""
                INSERT INTO report_analysis_policies (report_id, policy_id)
                VALUES (:reportId, :policyId)
                """, Map.of("reportId", reportId, "policyId", policyId)));
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

    public record AnalysisLock(
            AnalysisStatus status,
            String resultHash,
            ReportTargetType targetType
    ) {
    }
}
