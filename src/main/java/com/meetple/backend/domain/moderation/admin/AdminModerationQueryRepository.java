package com.meetple.backend.domain.moderation.admin;

import static com.meetple.backend.domain.moderation.admin.AdminModerationContracts.*;
import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;

import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportReviewStatus;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminModerationQueryRepository {
    private static final String SUMMARY_SELECT = """
            SELECT report.id AS report_id,
                   report.target_type,
                   report.target_id,
                   target_member.id AS target_member_id,
                   target_member.nickname AS target_nickname,
                   report.reason,
                   report.review_status,
                   analysis.status AS analysis_status,
                   analysis.risk_level,
                   analysis.priority,
                   analysis.confidence,
                   analysis.recommended_action,
                   CASE WHEN warning.report_id IS NULL THEN FALSE ELSE TRUE END AS automatic_warning_issued,
                   report.created_at,
                   report.resolved_at
            FROM reports report
            LEFT JOIN meetings target_meeting
              ON report.target_type = 'MEETING'
             AND target_meeting.id = report.target_id
            LEFT JOIN chat_messages target_message
              ON report.target_type = 'CHAT_MESSAGE'
             AND target_message.id = report.target_id
            LEFT JOIN members target_member
              ON target_member.id = CASE report.target_type
                  WHEN 'MEMBER' THEN report.target_id
                  WHEN 'MEETING' THEN target_meeting.host_id
                  WHEN 'CHAT_MESSAGE' THEN target_message.sender_id
              END
            LEFT JOIN report_analyses analysis ON analysis.report_id = report.id
            LEFT JOIN report_warnings warning ON warning.report_id = report.id
            """;
    private static final String DETAIL_SELECT = """
            SELECT report.id AS report_id,
                   report.target_type,
                   report.target_id,
                   target_member.id AS target_member_id,
                   target_member.nickname AS target_nickname,
                   report.reason,
                   report.review_status,
                   analysis.status AS analysis_status,
                   analysis.risk_level,
                   analysis.priority,
                   analysis.confidence,
                   analysis.recommended_action,
                   CASE WHEN warning.report_id IS NULL THEN FALSE ELSE TRUE END AS automatic_warning_issued,
                   report.reporter_member_id,
                   reporter.nickname AS reporter_nickname,
                   report.other_description,
                   report.target_snapshot,
                   analysis.report_type,
                   analysis.summary AS analysis_summary,
                   analysis.rationale,
                   analysis.failure_code,
                   analysis.completed_at AS analysis_completed_at,
                   target_member.suspended_until,
                   target_member.permanently_suspended_at,
                   target_member.suspension_report_id,
                   target_meeting.id AS target_meeting_id,
                   target_meeting.deleted_at AS meeting_deleted_at,
                   target_meeting.moderation_deleted_by_report_id,
                   report.created_at,
                   report.resolved_at
            FROM reports report
            JOIN members reporter ON reporter.id = report.reporter_member_id
            LEFT JOIN meetings target_meeting
              ON report.target_type = 'MEETING'
             AND target_meeting.id = report.target_id
            LEFT JOIN chat_messages target_message
              ON report.target_type = 'CHAT_MESSAGE'
             AND target_message.id = report.target_id
            LEFT JOIN members target_member
              ON target_member.id = CASE report.target_type
                  WHEN 'MEMBER' THEN report.target_id
                  WHEN 'MEETING' THEN target_meeting.host_id
                  WHEN 'CHAT_MESSAGE' THEN target_message.sender_id
              END
            LEFT JOIN report_analyses analysis ON analysis.report_id = report.id
            LEFT JOIN report_warnings warning ON warning.report_id = report.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminModerationQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<ReportSummary> findReports(
            ReportReviewStatus reviewStatus,
            AnalysisStatus analysisStatus,
            Pageable pageable,
            boolean ascending
    ) {
        String conditions = buildConditions(reviewStatus, analysisStatus);
        MapSqlParameterSource parameters = parameters(reviewStatus, analysisStatus)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        List<ReportSummary> content = jdbc.query(
                SUMMARY_SELECT + conditions
                        + " ORDER BY report.created_at " + (ascending ? "ASC" : "DESC")
                        + ", report.id " + (ascending ? "ASC" : "DESC")
                        + " LIMIT :limit OFFSET :offset",
                parameters,
                this::mapSummary
        );
        long total = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM reports report
                LEFT JOIN report_analyses analysis ON analysis.report_id = report.id
                """ + conditions,
                parameters,
                Long.class
        );
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<ReportDetail> findReport(long reportId) {
        List<ReportDetailRow> rows = jdbc.query(
                DETAIL_SELECT + " WHERE report.id = :reportId",
                java.util.Map.of("reportId", reportId),
                (rs, rowNum) -> new ReportDetailRow(
                        mapSummary(rs, rowNum),
                        rs.getLong("reporter_member_id"),
                        rs.getString("reporter_nickname"),
                        rs.getString("other_description"),
                        rs.getString("target_snapshot"),
                        mapAnalysis(rs),
                        new TargetState(
                                nullableLong(rs, "target_member_id"),
                                localDateTime(rs, "suspended_until"),
                                localDateTime(rs, "permanently_suspended_at"),
                                nullableLong(rs, "suspension_report_id"),
                                nullableLong(rs, "target_meeting_id"),
                                localDateTime(rs, "meeting_deleted_at"),
                                nullableLong(rs, "moderation_deleted_by_report_id")
                        )
                )
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ReportDetailRow row = rows.getFirst();
        return Optional.of(new ReportDetail(
                row.summary(),
                row.reporterMemberId(),
                row.reporterNickname(),
                row.otherDescription(),
                row.evidenceContent(),
                row.analysis(),
                findPolicies(reportId),
                findWarnings(reportId, row.targetState().memberId()),
                findActions(reportId, row.targetState()),
                row.targetState()
        ));
    }

    public Optional<Long> findTargetMemberId(long reportId) {
        List<Long> rows = jdbc.query("""
                SELECT CASE report.target_type
                           WHEN 'MEMBER' THEN report.target_id
                           WHEN 'MEETING' THEN meeting.host_id
                           WHEN 'CHAT_MESSAGE' THEN message.sender_id
                       END AS target_member_id
                FROM reports report
                LEFT JOIN meetings meeting
                  ON report.target_type = 'MEETING' AND meeting.id = report.target_id
                LEFT JOIN chat_messages message
                  ON report.target_type = 'CHAT_MESSAGE' AND message.id = report.target_id
                WHERE report.id = :reportId
                """, java.util.Map.of("reportId", reportId),
                (rs, rowNum) -> nullableLong(rs, "target_member_id"));
        return rows.stream().filter(Objects::nonNull).findFirst();
    }

    public Optional<MeetingModerationState> lockMeeting(long meetingId) {
        return jdbc.query("""
                SELECT deleted_at, moderation_deleted_by_report_id
                FROM meetings
                WHERE id = :meetingId
                FOR UPDATE
                """, java.util.Map.of("meetingId", meetingId),
                (rs, rowNum) -> new MeetingModerationState(
                        localDateTime(rs, "deleted_at"),
                        nullableLong(rs, "moderation_deleted_by_report_id")
                ))
                .stream()
                .findFirst();
    }

    public void updateMeetingDeletion(
            long meetingId,
            LocalDateTime deletedAt,
            Long moderationReportId
    ) {
        int updated = jdbc.update("""
                UPDATE meetings
                SET deleted_at = :deletedAt,
                    moderation_deleted_by_report_id = :moderationReportId,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :meetingId
                """, new MapSqlParameterSource()
                .addValue("meetingId", meetingId)
                .addValue("deletedAt", deletedAt)
                .addValue("moderationReportId", moderationReportId));
        if (updated != 1) {
            throw new IllegalStateException("모임 제재 상태를 변경할 수 없습니다.");
        }
    }

    public boolean wasAutomaticallyWarned(long reportId) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM report_warnings WHERE report_id = :reportId)
                """, java.util.Map.of("reportId", reportId), Boolean.class);
        return Boolean.TRUE.equals(found);
    }

    private List<PolicyBasis> findPolicies(long reportId) {
        return jdbc.query("""
                SELECT policy.id, policy.policy_code, policy.title, policy.version
                FROM report_analysis_policies basis
                JOIN moderation_policies policy ON policy.id = basis.policy_id
                WHERE basis.report_id = :reportId
                ORDER BY policy.id
                """, java.util.Map.of("reportId", reportId), (rs, rowNum) -> new PolicyBasis(
                rs.getLong("id"),
                rs.getString("policy_code"),
                rs.getString("title"),
                rs.getInt("version")
        ));
    }

    private List<WarningHistory> findWarnings(long reportId, Long targetMemberId) {
        String condition = targetMemberId == null
                ? "warning.report_id = :reportId"
                : "warning.target_member_id = :targetMemberId";
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("reportId", reportId)
                .addValue("targetMemberId", targetMemberId);
        return jdbc.query("""
                SELECT warning.report_id, warning.target_member_id, warning.created_at
                FROM report_warnings warning
                """ + " WHERE " + condition
                + " ORDER BY warning.created_at DESC, warning.report_id DESC",
                parameters, (rs, rowNum) -> new WarningHistory(
                rs.getLong("report_id"),
                rs.getLong("target_member_id"),
                localDateTime(rs, "created_at")
        ));
    }

    private List<ActionHistory> findActions(long reportId, TargetState targetState) {
        StringBuilder condition = new StringBuilder("action.report_id = :reportId");
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("reportId", reportId);
        if (targetState.memberId() != null) {
            condition.append(" OR action.target_member_id = :targetMemberId");
            parameters.addValue("targetMemberId", targetState.memberId());
        }
        if (targetState.meetingId() != null) {
            condition.append(" OR action.target_meeting_id = :targetMeetingId");
            parameters.addValue("targetMeetingId", targetState.meetingId());
        }
        return jdbc.query("""
                SELECT action.id,
                       action.report_id,
                       action.administrator_member_id,
                       administrator.nickname AS administrator_nickname,
                       action.action_type,
                       action.target_member_id,
                       action.target_meeting_id,
                       action.reason,
                       action.effective_until,
                       action.created_at
                FROM moderation_actions action
                JOIN members administrator ON administrator.id = action.administrator_member_id
                """ + " WHERE " + condition
                + " ORDER BY action.created_at DESC, action.id DESC",
                parameters, (rs, rowNum) -> new ActionHistory(
                rs.getLong("id"),
                rs.getLong("report_id"),
                rs.getLong("administrator_member_id"),
                rs.getString("administrator_nickname"),
                AdminModerationActionType.valueOf(rs.getString("action_type")),
                nullableLong(rs, "target_member_id"),
                nullableLong(rs, "target_meeting_id"),
                rs.getString("reason"),
                localDateTime(rs, "effective_until"),
                localDateTime(rs, "created_at")
        ));
    }

    private String buildConditions(ReportReviewStatus reviewStatus, AnalysisStatus analysisStatus) {
        StringBuilder conditions = new StringBuilder(" WHERE 1 = 1");
        if (reviewStatus != null) {
            conditions.append(" AND report.review_status = :reviewStatus");
        }
        if (analysisStatus != null) {
            conditions.append(" AND analysis.status = :analysisStatus");
        }
        return conditions.toString();
    }

    private MapSqlParameterSource parameters(ReportReviewStatus reviewStatus, AnalysisStatus analysisStatus) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (reviewStatus != null) {
            parameters.addValue("reviewStatus", reviewStatus.name());
        }
        if (analysisStatus != null) {
            parameters.addValue("analysisStatus", analysisStatus.name());
        }
        return parameters;
    }

    private ReportSummary mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new ReportSummary(
                rs.getLong("report_id"),
                ReportTargetType.valueOf(rs.getString("target_type")),
                rs.getLong("target_id"),
                nullableLong(rs, "target_member_id"),
                rs.getString("target_nickname"),
                ReportReason.valueOf(rs.getString("reason")),
                ReportReviewStatus.valueOf(rs.getString("review_status")),
                enumValue(rs, "analysis_status", AnalysisStatus.class),
                enumValue(rs, "risk_level", RiskLevel.class),
                enumValue(rs, "priority", ModerationPriority.class),
                rs.getBigDecimal("confidence"),
                enumValue(rs, "recommended_action", RecommendedAction.class),
                rs.getBoolean("automatic_warning_issued"),
                localDateTime(rs, "created_at"),
                localDateTime(rs, "resolved_at")
        );
    }

    private AnalysisDetail mapAnalysis(ResultSet rs) throws SQLException {
        AnalysisStatus status = enumValue(rs, "analysis_status", AnalysisStatus.class);
        if (status == null) {
            return null;
        }
        return new AnalysisDetail(
                status,
                enumValue(rs, "report_type", ModerationReportType.class),
                enumValue(rs, "risk_level", RiskLevel.class),
                enumValue(rs, "priority", ModerationPriority.class),
                rs.getString("analysis_summary"),
                rs.getString("rationale"),
                rs.getBigDecimal("confidence"),
                enumValue(rs, "recommended_action", RecommendedAction.class),
                rs.getString("failure_code"),
                localDateTime(rs, "analysis_completed_at")
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    private static <E extends Enum<E>> E enumValue(ResultSet rs, String column, Class<E> type)
            throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    private record ReportDetailRow(
            ReportSummary summary,
            long reporterMemberId,
            String reporterNickname,
            String otherDescription,
            String evidenceContent,
            AnalysisDetail analysis,
            TargetState targetState
    ) {
    }

    public record MeetingModerationState(LocalDateTime deletedAt, Long moderationReportId) {
    }
}
