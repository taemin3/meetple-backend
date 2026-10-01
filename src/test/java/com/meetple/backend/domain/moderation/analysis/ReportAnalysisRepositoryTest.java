package com.meetple.backend.domain.moderation.analysis;

import static com.meetple.backend.domain.moderation.analysis.ReportAnalysisContracts.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.Candidate;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyTargetType;
import com.meetple.backend.domain.moderation.policy.ModerationPolicyType;
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

    @BeforeEach
    void prepare() {
        var datasource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(datasource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(datasource);
        repository = new ReportAnalysisRepository(new NamedParameterJdbcTemplate(datasource));
        jdbc.execute("TRUNCATE members, moderation_policies CASCADE");
        insertFixtures();
    }

    @Test
    void persistsContextRetrievalAndGroundedCompletion() {
        repository.initialize(
                10L,
                ReportTargetType.CHAT_MESSAGE,
                100L,
                "신고 당시 메시지",
                "a".repeat(64)
        );

        Context context = repository.findContext(10L).orElseThrow();
        assertThat(context.evidence())
                .extracting(Evidence::content)
                .containsExactly("신고 당시 메시지");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM report_analyses WHERE report_id=10",
                String.class
        )).isEqualTo("PENDING");

        long retrievalId = repository.recordRetrieval(
                10L,
                "text-embedding-3-small",
                "폭언",
                ModerationPolicyType.ABUSE_OR_HARASSMENT,
                List.of(candidate())
        );
        long evidenceId = context.evidence().getFirst().evidenceId();
        CompleteRequest request = new CompleteRequest(
                retrievalId,
                ModerationReportType.ABUSE_OR_HARASSMENT,
                RiskLevel.HIGH,
                ModerationPriority.HIGH,
                "폭언 신고",
                "증거와 정책에 근거한 판단",
                List.of(evidenceId),
                List.of(40L),
                new BigDecimal("0.91"),
                RecommendedAction.SUSPEND_3_DAYS
        );
        repository.complete(
                10L,
                request,
                "b".repeat(64),
                request.evidenceIds(),
                request.policyIds()
        );

        assertThat(jdbc.queryForObject(
                "SELECT status FROM report_analyses WHERE report_id=10",
                String.class
        )).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_analysis_evidence_selections WHERE report_id=10",
                Integer.class
        )).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM report_analysis_policy_selections WHERE report_id=10",
                Integer.class
        )).isOne();
    }

    private void insertFixtures() {
        jdbc.update("""
                INSERT INTO members
                    (id, email, password, nickname, role, created_at, updated_at)
                VALUES (1, 'reporter@example.com', 'password', 'reporter', 'USER',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO reports
                    (id, reporter_member_id, target_type, target_id, reason,
                     created_at, updated_at)
                VALUES (10, 1, 'CHAT_MESSAGE', 100, 'ABUSE_OR_HARASSMENT',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO moderation_policies
                    (id, policy_code, title, policy_type, target_type, effective_from,
                     active, version, created_at, updated_at)
                VALUES (40, 'ABUSE-001', '괴롭힘 금지', 'ABUSE_OR_HARASSMENT', 'ALL',
                        DATE '2026-01-01', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO moderation_policy_chunks
                    (id, policy_id, clause_code, chunk_order, content, content_hash,
                     created_at, updated_at)
                VALUES (41, 40, '1.1', 0, '타인을 괴롭히면 안 됩니다.', ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "c".repeat(64));
    }

    private Candidate candidate() {
        return new Candidate(
                40L,
                41L,
                "ABUSE-001",
                "괴롭힘 금지",
                ModerationPolicyType.ABUSE_OR_HARASSMENT,
                ModerationPolicyTargetType.ALL,
                1,
                "1.1",
                "타인을 괴롭히면 안 됩니다.",
                "c".repeat(64),
                LocalDate.of(2026, 1, 1),
                null,
                true,
                0.1,
                0.9
        );
    }
}
