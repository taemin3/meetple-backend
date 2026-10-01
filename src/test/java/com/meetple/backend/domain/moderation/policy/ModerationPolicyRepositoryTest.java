package com.meetple.backend.domain.moderation.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.global.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class ModerationPolicyRepositoryTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName
            .parse("meetple-postgres:16-3.5-bigm-vector0.8.6")
            .asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres", "-c", "shared_preload_libraries=pg_bigm,pg_stat_statements");

    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 10, 1);
    private static final String MODEL = "test-embedding-model";
    private JdbcTemplate jdbc;
    private ModerationPolicyRepository repository;

    @BeforeEach
    void prepare() {
        var datasource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(datasource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(datasource);
        repository = new ModerationPolicyRepository(new NamedParameterJdbcTemplate(datasource));
        jdbc.execute("TRUNCATE moderation_policies CASCADE");
        var fixture = new ResourceDatabasePopulator(
                new ClassPathResource("fixtures/moderation-policies.sql")
        );
        fixture.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        fixture.execute(datasource);
    }

    @Test
    void combinesKeywordAndSameModelVectorCandidates() {
        insertEmbedding(1001, embedding(0), MODEL, "1".repeat(64));
        insertEmbedding(1002, embedding(0), MODEL, "2".repeat(64));
        insertEmbedding(1003, embedding(0), "other-model", "3".repeat(64));

        var result = repository.search(
                "광고",
                ReportTargetType.CHAT_MESSAGE,
                null,
                embedding(0),
                MODEL,
                20,
                EFFECTIVE_DATE
        );

        assertThat(result.items())
                .extracting(ModerationPolicyContracts.Candidate::policyChunkId)
                .containsExactlyInAnyOrder(1001L, 1002L);
        var spam = result.items().stream()
                .filter(candidate -> candidate.policyChunkId() == 1001L)
                .findFirst()
                .orElseThrow();
        var harassment = result.items().stream()
                .filter(candidate -> candidate.policyChunkId() == 1002L)
                .findFirst()
                .orElseThrow();
        assertThat(spam.keywordMatched()).isTrue();
        assertThat(spam.hybridScore()).isGreaterThan(harassment.hybridScore());
    }

    @Test
    void filtersInactiveExpiredWrongTargetAndPolicyType() {
        insertEmbedding(1001, embedding(0), MODEL, "1".repeat(64));
        insertEmbedding(1002, embedding(0), MODEL, "2".repeat(64));
        insertEmbedding(1003, embedding(0), MODEL, "3".repeat(64));
        jdbc.update("UPDATE moderation_policies SET active=FALSE WHERE id=101");
        jdbc.update("UPDATE moderation_policies SET effective_to=DATE '2026-09-30' WHERE id=102");

        var result = repository.search(
                "일치하지않는키워드",
                ReportTargetType.MEMBER,
                ModerationPolicyType.SAFETY,
                embedding(0),
                MODEL,
                20,
                EFFECTIVE_DATE
        );

        assertThat(result.items()).isEmpty();
    }

    @Test
    void excludesFuturePolicyFromSearchButAllowsPrecomputingItsEmbedding() {
        jdbc.update("""
                INSERT INTO moderation_policies
                    (id, policy_code, title, policy_type, target_type, effective_from,
                     active, version, created_at, updated_at)
                VALUES (104, 'COMMUNITY-FUTURE', '시행 예정 정책', 'SPAM', 'ALL',
                        DATE '2026-10-02', TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO moderation_policy_chunks
                    (id, policy_id, clause_code, chunk_order, content, content_hash,
                     created_at, updated_at)
                VALUES (1004, 104, 'FUTURE-1', 0, '시행 예정인 광고 정책입니다.', ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "4".repeat(64));
        insertEmbedding(1004, embedding(0), MODEL, "4".repeat(64));

        var searchResult = repository.search(
                "시행 예정",
                ReportTargetType.MEMBER,
                ModerationPolicyType.SPAM,
                embedding(0),
                MODEL,
                20,
                EFFECTIVE_DATE
        );

        assertThat(searchResult.items()).isEmpty();

        jdbc.update("DELETE FROM moderation_policy_embeddings WHERE policy_chunk_id=1004");
        assertThat(repository.findEmbeddingJobs(MODEL, 100, EFFECTIVE_DATE).items())
                .extracting(ModerationPolicyContracts.EmbeddingJob::policyChunkId)
                .contains(1004L);
    }

    @Test
    void returnsOnlyMissingOrStaleEmbeddingJobsForCurrentPolicies() {
        insertEmbedding(1001, embedding(0), MODEL, "1".repeat(64));
        insertEmbedding(1002, embedding(0), MODEL, "0".repeat(64));
        jdbc.update("UPDATE moderation_policies SET active=FALSE WHERE id=103");

        var jobs = repository.findEmbeddingJobs(MODEL, 100, EFFECTIVE_DATE);

        assertThat(jobs.items())
                .extracting(ModerationPolicyContracts.EmbeddingJob::policyChunkId)
                .containsExactly(1002L);
    }

    @Test
    void embeddingUpsertRejectsStaleContentHash() {
        assertThatThrownBy(() -> repository.upsertEmbedding(
                1001,
                MODEL,
                "0".repeat(64),
                embedding(0)
        )).isInstanceOf(BadRequestException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM moderation_policy_embeddings", Integer.class
        )).isZero();
    }

    @Test
    void embeddingUpsertStoresOnlyMatchingChunkVersion() {
        repository.upsertEmbedding(1001, MODEL, "1".repeat(64), embedding(0));

        assertThat(jdbc.queryForObject("""
                SELECT content_hash
                FROM moderation_policy_embeddings
                WHERE policy_chunk_id=1001 AND embedding_model=?
                """, String.class, MODEL)).isEqualTo("1".repeat(64));
    }

    private void insertEmbedding(
            long chunkId,
            List<Double> embedding,
            String model,
            String contentHash
    ) {
        jdbc.update("""
                INSERT INTO moderation_policy_embeddings
                    (policy_chunk_id, embedding, embedding_model, content_hash, embedded_at)
                VALUES (?, CAST(? AS vector), ?, ?, CURRENT_TIMESTAMP)
                """, chunkId, vectorLiteral(embedding), model, contentHash);
    }

    private List<Double> embedding(int activeDimension) {
        var values = new ArrayList<>(Collections.nCopies(1536, 0.0));
        values.set(activeDimension, 1.0);
        return values;
    }

    private String vectorLiteral(List<Double> embedding) {
        return embedding.stream().map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
