package com.meetple.backend.domain.moderation.admin;

import static com.meetple.backend.domain.moderation.admin.AdminModerationContracts.ReportDetail;
import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.AnalysisStatus;
import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.entity.MemberRole;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.moderation.entity.Report;
import com.meetple.backend.domain.moderation.entity.ReportReason;
import com.meetple.backend.domain.moderation.entity.ReportReviewStatus;
import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.repository.ReportRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
@Import(AdminModerationQueryRepository.class)
class AdminModerationQueryRepositoryTest {
    @Autowired AdminModerationQueryRepository repository;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired MemberRepository memberRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired ModerationActionRepository actionRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MeetingRepository meetingRepository;

    @BeforeEach
    void prepareJdbcModerationTables() {
        jdbc.getJdbcTemplate().execute(
                "ALTER TABLE reports ADD COLUMN IF NOT EXISTS target_snapshot CLOB"
        );
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS report_analyses (
                    report_id BIGINT PRIMARY KEY,
                    status VARCHAR(30) NOT NULL,
                    report_type VARCHAR(50),
                    risk_level VARCHAR(20),
                    priority VARCHAR(20),
                    summary VARCHAR(500),
                    rationale VARCHAR(1000),
                    confidence NUMERIC(5, 4),
                    recommended_action VARCHAR(50),
                    failure_code VARCHAR(100),
                    completed_at TIMESTAMP
                )
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS report_warnings (
                    report_id BIGINT PRIMARY KEY,
                    target_member_id BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS moderation_policies (
                    id BIGINT PRIMARY KEY,
                    policy_code VARCHAR(100) NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    version INTEGER NOT NULL
                )
                """);
        jdbc.getJdbcTemplate().execute("""
                CREATE TABLE IF NOT EXISTS report_analysis_policies (
                    report_id BIGINT NOT NULL,
                    policy_id BIGINT NOT NULL,
                    PRIMARY KEY (report_id, policy_id)
                )
                """);
    }

    @Test
    void listAndDetailIncludeAnalysisPolicyAndActionHistory() {
        Member reporter = memberRepository.save(Member.createUser(
                "reporter@meetple.com", "password", "신고자", null));
        Member target = memberRepository.save(Member.createUser(
                "target@meetple.com", "password", "신고대상", null));
        Member administrator = Member.createUser(
                "admin@meetple.com", "password", "관리자", null);
        ReflectionTestUtils.setField(administrator, "role", MemberRole.ADMIN);
        administrator = memberRepository.save(administrator);

        Report report = reportRepository.saveAndFlush(Report.create(
                reporter,
                ReportTargetType.MEMBER,
                target.getId(),
                ReportReason.SPAM,
                null
        ));
        jdbc.update("""
                UPDATE reports SET target_snapshot = :snapshot WHERE id = :reportId
                """, Map.of("snapshot", "광고성 프로필 소개", "reportId", report.getId()));
        jdbc.update("""
                INSERT INTO report_analyses (
                    report_id, status, report_type, risk_level, priority, summary, rationale,
                    confidence, recommended_action, completed_at
                ) VALUES (
                    :reportId, 'COMPLETED', 'SPAM', 'LOW', 'NORMAL', '광고 신고',
                    '증거와 정책이 일치함', 0.9700, 'WARNING', CURRENT_TIMESTAMP
                )
                """, Map.of("reportId", report.getId()));
        jdbc.update("""
                INSERT INTO moderation_policies (id, policy_code, title, version)
                VALUES (501, 'SPAM-001', '광고 및 도배 금지', 1)
                """, Map.of());
        jdbc.update("""
                INSERT INTO report_analysis_policies (report_id, policy_id)
                VALUES (:reportId, 501)
                """, Map.of("reportId", report.getId()));
        jdbc.update("""
                INSERT INTO report_warnings (report_id, target_member_id)
                VALUES (:reportId, :targetMemberId)
                """, Map.of("reportId", report.getId(), "targetMemberId", target.getId()));
        actionRepository.saveAndFlush(ModerationAction.create(
                report.getId(),
                administrator.getId(),
                AdminModerationActionType.WARNING,
                target.getId(),
                null,
                "정책 위반 확인",
                null
        ));

        var page = repository.findReports(
                ReportReviewStatus.PENDING,
                AnalysisStatus.COMPLETED,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                false
        );
        ReportDetail detail = repository.findReport(report.getId()).orElseThrow();

        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.reportId()).isEqualTo(report.getId());
            assertThat(summary.targetMemberId()).isEqualTo(target.getId());
            assertThat(summary.targetNickname()).isEqualTo("신고대상");
            assertThat(summary.recommendedAction().name()).isEqualTo("WARNING");
        });
        assertThat(detail.evidenceContent()).isEqualTo("광고성 프로필 소개");
        assertThat(detail.analysis().summary()).isEqualTo("광고 신고");
        assertThat(detail.policyBasis()).singleElement()
                .extracting(policy -> policy.policyCode())
                .isEqualTo("SPAM-001");
        assertThat(detail.warnings()).singleElement()
                .extracting(warning -> warning.reportId())
                .isEqualTo(report.getId());
        assertThat(detail.actions()).singleElement().satisfies(action -> {
            assertThat(action.administratorNickname()).isEqualTo("관리자");
            assertThat(action.reason()).isEqualTo("정책 위반 확인");
        });
    }

    @Test
    void restoreMeetingCompletesEndedOpenMeetingInSameUpdate() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 9, 0);
        Member host = memberRepository.save(Member.createUser(
                "host@meetple.com", "password", "모임장", null));
        Category category = categoryRepository.save(Category.create("운동"));
        Meeting meeting = meetingRepository.saveAndFlush(Meeting.create(
                host,
                category,
                "아침 러닝",
                "함께 달려요",
                "한강공원",
                "서울시 영등포구",
                new BigDecimal("37.528300"),
                new BigDecimal("126.932600"),
                5,
                now.minusHours(2),
                now.minusHours(1),
                null
        ));
        jdbc.update("""
                UPDATE meetings
                SET deleted_at = :deletedAt,
                    moderation_deleted_by_report_id = 10
                WHERE id = :meetingId
                """, Map.of("deletedAt", now.minusMinutes(30), "meetingId", meeting.getId()));

        repository.restoreMeeting(meeting.getId(), now);

        Map<String, Object> state = jdbc.queryForMap("""
                SELECT status, deleted_at, moderation_deleted_by_report_id
                FROM meetings
                WHERE id = :meetingId
                """, Map.of("meetingId", meeting.getId()));
        assertThat(state.get("status")).isEqualTo("COMPLETED");
        assertThat(state.get("deleted_at")).isNull();
        assertThat(state.get("moderation_deleted_by_report_id")).isNull();
    }
}
