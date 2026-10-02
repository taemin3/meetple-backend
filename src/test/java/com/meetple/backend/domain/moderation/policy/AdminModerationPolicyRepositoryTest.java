package com.meetple.backend.domain.moderation.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.moderation.policy.AdminModerationPolicyRepository.ClauseRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
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
class AdminModerationPolicyRepositoryTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("meetple-postgres:16-3.5-bigm-vector0.8.6")
            .asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres", "-c", "shared_preload_libraries=pg_bigm,pg_stat_statements");

    private static final String MODEL = "text-embedding-3-small";
    private AdminModerationPolicyRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        var datasource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(datasource).locations("classpath:db/migration").load().migrate();
        repository = new AdminModerationPolicyRepository(
                new NamedParameterJdbcTemplate(datasource)
        );
        jdbc = new JdbcTemplate(datasource);
        jdbc.execute("TRUNCATE moderation_policies CASCADE");
    }

    @Test
    void insertsPolicyAndReportsEmbeddingReadiness() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 9, 0);
        String contentHash = "a".repeat(64);
        long policyId = repository.insertPolicy(
                "COMMUNITY-SPAM",
                "스팸 금지",
                ModerationPolicyType.SPAM,
                ModerationPolicyTargetType.ALL,
                LocalDate.of(2026, 10, 3),
                null,
                1,
                now
        );
        repository.insertClauses(
                policyId,
                List.of(new ClauseRow("SPAM-1", 0, "반복 광고 금지", contentHash)),
                now
        );

        var beforeEmbedding = repository.findAll(null, false, 0, 20, MODEL);

        assertThat(beforeEmbedding.getContent()).singleElement().satisfies(policy -> {
            assertThat(policy.policyId()).isEqualTo(policyId);
            assertThat(policy.version()).isEqualTo(1);
            assertThat(policy.active()).isFalse();
            assertThat(policy.clauseCount()).isEqualTo(1);
            assertThat(policy.missingEmbeddingCount()).isEqualTo(1);
        });
        assertThat(repository.findClauses(policyId, MODEL)).singleElement()
                .satisfies(clause -> assertThat(clause.embedded()).isFalse());

        long chunkId = repository.findClauses(policyId, MODEL).getFirst().policyChunkId();
        jdbc.update("""
                INSERT INTO moderation_policy_embeddings (
                    policy_chunk_id, embedding_model, embedding, content_hash, embedded_at
                ) VALUES (?, ?, CAST(? AS vector), ?, CURRENT_TIMESTAMP)
                """, chunkId, MODEL, vectorLiteral(), contentHash);

        assertThat(repository.countMissingEmbeddings(policyId, MODEL)).isZero();
        assertThat(repository.findClauses(policyId, MODEL)).singleElement()
                .satisfies(clause -> assertThat(clause.embedded()).isTrue());
    }

    @Test
    void deactivatesOtherVersionAndReturnsChangedPolicyIds() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 9, 0);
        long previousPolicyId = repository.insertPolicy(
                "COMMUNITY-SPAM",
                "스팸 금지",
                ModerationPolicyType.SPAM,
                ModerationPolicyTargetType.ALL,
                LocalDate.of(2026, 10, 1),
                null,
                1,
                now
        );
        long nextPolicyId = repository.insertPolicy(
                "COMMUNITY-SPAM",
                "스팸 금지 개정",
                ModerationPolicyType.SPAM,
                ModerationPolicyTargetType.ALL,
                LocalDate.of(2026, 10, 2),
                null,
                2,
                now
        );
        jdbc.update("UPDATE moderation_policies SET active = TRUE WHERE id = ?", previousPolicyId);

        List<Long> deactivatedPolicyIds = repository.deactivateOtherVersions(
                nextPolicyId,
                "COMMUNITY-SPAM",
                now.plusHours(1)
        );

        assertThat(deactivatedPolicyIds).containsExactly(previousPolicyId);
        assertThat(repository.findById(previousPolicyId)).get()
                .extracting(AdminModerationPolicyRepository.PolicyRow::active)
                .isEqualTo(false);
    }

    private static String vectorLiteral() {
        return java.util.stream.IntStream.range(0, ModerationPolicyContracts.EMBEDDING_DIMENSIONS)
                .mapToObj(index -> index == 0 ? "1" : "0")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
