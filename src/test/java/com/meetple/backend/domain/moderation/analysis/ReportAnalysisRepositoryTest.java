package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.warning.AutomaticWarningRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class ReportAnalysisRepositoryTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("meetple-postgres:16-3.5-bigm-vector0.8.6")
            .asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres", "-c", "shared_preload_libraries=pg_bigm,pg_stat_statements");

    private JdbcTemplate jdbc;
    private ReportAnalysisRepository repository;
    private AutomaticWarningRepository warningRepository;

    @BeforeEach
    void prepare() {
        var datasource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(datasource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(datasource);
        repository = new ReportAnalysisRepository(new NamedParameterJdbcTemplate(datasource));
        warningRepository = new AutomaticWarningRepository(new NamedParameterJdbcTemplate(datasource));
        jdbc.execute("TRUNCATE members, moderation_policies CASCADE");
        insertFixtures();
    }

    @Test
    void persistsReportSnapshotAnalysisAndAppliedPolicies() {
        repository.initialize(10L, "신고 당시 메시지", "a".repeat(64));

        Context context = repository.findContext(10L).orElseThrow();
        assertThat(context.evidence())
                .containsExactly(new Evidence(
                        10L,
                        ReportTargetType.CHAT_MESSAGE,
                        "신고 당시 메시지"
                ));
        assertThat(jdbc.queryForObject(
                "SELECT target_snapshot_hash FROM reports WHERE id=10",
                String.class
        )).isEqualTo("a".repeat(64));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM report_analyses WHERE report_id=10",
                String.class
        )).isEqualTo("PENDING");

        CompleteRequest request = new CompleteRequest(
                ModerationReportType.ABUSE_OR_HARASSMENT,
                RiskLevel.HIGH,
                ModerationPriority.HIGH,
                "폭언 신고",
                "증거와 정책에 근거한 판단",
                List.of(10L),
                List.of(40L),
                new BigDecimal("0.91"),
                RecommendedAction.SUSPEND_3_DAYS
        );
        repository.complete(10L, request, "b".repeat(64), request.policyIds());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM report_analyses WHERE report_id=10",
                String.class
        )).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_analysis_policies WHERE report_id=10",
                Integer.class
        )).isOne();
    }

    @Test
    void reportSnapshotCannotBeOverwritten() {
        repository.initialize(10L, "최초 신고 메시지", "a".repeat(64));

        assertThatThrownBy(() -> repository.initialize(
                10L,
                "변경된 메시지",
                "b".repeat(64)
        )).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT target_snapshot FROM reports WHERE id=10",
                String.class
        )).isEqualTo("최초 신고 메시지");
    }

    @Test
    void expiredPolicyIsNotApplicableToAnalysisResult() {
        jdbc.update("UPDATE moderation_policies SET effective_to=DATE '2026-09-30' WHERE id=40");

        assertThat(repository.findApplicablePolicyIds(
                ReportTargetType.CHAT_MESSAGE,
                List.of(40L),
                LocalDate.of(2026, 10, 1)
        )).isEmpty();
    }

    @Test
    void automaticWarningResolvesTargetMemberAndIsIdempotent() {
        assertThat(warningRepository.insertIfAbsent(10L)).contains(2L);
        assertThat(warningRepository.insertIfAbsent(10L)).isEmpty();
        assertThat(warningRepository.insertIfAbsent(11L)).contains(2L);
        assertThat(warningRepository.insertIfAbsent(12L)).contains(2L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_warnings",
                Integer.class
        )).isEqualTo(3);
    }

    private void insertFixtures() {
        jdbc.update("""
                INSERT INTO members
                    (id, email, password, nickname, role, created_at, updated_at)
                VALUES
                    (1, 'reporter@example.com', 'password', 'reporter', 'USER',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (2, 'target@example.com', 'password', 'target', 'USER',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO meetings
                    (id, title, content, location_name, address, latitude, longitude,
                     max_people, current_people, meeting_date, status, host_id, category_id,
                     created_at, updated_at)
                VALUES
                    (20, '테스트 모임', '테스트 내용', '서울', '서울', 37.5, 127.0,
                     10, 1, TIMESTAMP '2026-10-02 12:00:00', 'RECRUITING', 2, 1,
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO chat_messages
                    (id, meeting_id, sender_id, room_sequence, client_message_id, content,
                     created_at, updated_at)
                VALUES
                    (100, 20, 2, 1, '00000000-0000-0000-0000-000000000100', '광고 메시지',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO reports
                    (id, reporter_member_id, target_type, target_id, reason,
                     created_at, updated_at)
                VALUES
                    (10, 1, 'CHAT_MESSAGE', 100, 'ABUSE_OR_HARASSMENT',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (11, 1, 'MEMBER', 2, 'SPAM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (12, 1, 'MEETING', 20, 'SPAM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO moderation_policies
                    (id, policy_code, title, policy_type, target_type, effective_from,
                     active, version, created_at, updated_at)
                VALUES (40, 'ABUSE-001', '괴롭힘 금지', 'ABUSE_OR_HARASSMENT', 'ALL',
                        DATE '2026-01-01', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }
}
