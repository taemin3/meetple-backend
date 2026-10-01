package com.meetple.backend.domain.moderation.warning;

import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AutomaticWarningRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AutomaticWarningRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> insertIfAbsent(long reportId) {
        return jdbc.query("""
                WITH resolved_target AS (
                    SELECT CASE report.target_type
                               WHEN 'MEMBER' THEN report.target_id
                               WHEN 'MEETING' THEN meeting.host_id
                               WHEN 'CHAT_MESSAGE' THEN chat_message.sender_id
                           END AS target_member_id
                    FROM reports report
                    LEFT JOIN meetings meeting
                      ON report.target_type = 'MEETING'
                     AND meeting.id = report.target_id
                    LEFT JOIN chat_messages chat_message
                      ON report.target_type = 'CHAT_MESSAGE'
                     AND chat_message.id = report.target_id
                    WHERE report.id = :reportId
                ), warning_target AS (
                    SELECT member.id AS target_member_id
                    FROM resolved_target
                    JOIN members member ON member.id = resolved_target.target_member_id
                    WHERE member.deleted_at IS NULL
                )
                INSERT INTO report_warnings (report_id, target_member_id)
                SELECT :reportId, target_member_id
                FROM warning_target
                WHERE target_member_id IS NOT NULL
                ON CONFLICT (report_id) DO NOTHING
                RETURNING target_member_id
                """, Map.of("reportId", reportId), (rs, rowNum) -> rs.getLong("target_member_id"))
                .stream()
                .findFirst();
    }
}
