package com.meetple.backend.domain.moderation.policy;

import static com.meetple.backend.domain.moderation.policy.ModerationPolicyContracts.*;

import com.meetple.backend.domain.moderation.entity.ReportTargetType;
import com.meetple.backend.global.exception.BadRequestException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ModerationPolicyRepository {
    private static final int MIN_VECTOR_CANDIDATE_LIMIT = 100;
    private static final int VECTOR_CANDIDATE_MULTIPLIER = 10;
    private static final int HNSW_MAX_SCAN_TUPLES = 50_000;
    private static final RowMapper<Candidate> CANDIDATE_MAPPER = (rs, rowNum) -> new Candidate(
            rs.getLong("policy_id"),
            rs.getLong("policy_chunk_id"),
            rs.getString("policy_code"),
            rs.getString("policy_title"),
            ModerationPolicyType.valueOf(rs.getString("policy_type")),
            ModerationPolicyTargetType.valueOf(rs.getString("target_type")),
            rs.getInt("policy_version"),
            rs.getString("clause_code"),
            rs.getString("content"),
            rs.getString("content_hash"),
            rs.getObject("effective_from", LocalDate.class),
            rs.getObject("effective_to", LocalDate.class),
            rs.getBoolean("keyword_matched"),
            rs.getDouble("semantic_distance"),
            rs.getDouble("hybrid_score")
    );
    private static final RowMapper<EmbeddingJob> EMBEDDING_JOB_MAPPER = (rs, rowNum) ->
            new EmbeddingJob(
                    rs.getLong("policy_id"),
                    rs.getLong("policy_chunk_id"),
                    rs.getString("policy_code"),
                    rs.getInt("policy_version"),
                    rs.getString("clause_code"),
                    rs.getString("content"),
                    rs.getString("content_hash")
            );

    private final NamedParameterJdbcTemplate jdbc;

    public ModerationPolicyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Candidates search(
            String keyword,
            ReportTargetType targetType,
            ModerationPolicyType policyType,
            List<Double> queryEmbedding,
            String embeddingModel,
            int limit,
            LocalDate effectiveDate
    ) {
        configureFilteredHnswSearch();
        Map<String, Object> params = commonParameters(
                targetType, policyType, embeddingModel, effectiveDate
        );
        params.put("keyword", escapedKeyword(keyword));
        params.put("queryEmbedding", vectorLiteral(queryEmbedding));
        params.put(
                "vectorCandidateLimit",
                Math.max(MIN_VECTOR_CANDIDATE_LIMIT, limit * VECTOR_CANDIDATE_MULTIPLIER)
        );
        params.put("resultLimit", limit + 1);

        List<Candidate> rows = jdbc.query("""
                WITH semantic_candidates AS MATERIALIZED (
                    SELECT embedding.policy_chunk_id,
                           embedding.embedding <=> CAST(:queryEmbedding AS vector) AS semantic_distance
                    FROM moderation_policy_embeddings embedding
                    JOIN moderation_policy_chunks chunk
                      ON chunk.id = embedding.policy_chunk_id
                     AND chunk.content_hash = embedding.content_hash
                    JOIN moderation_policies policy ON policy.id = chunk.policy_id
                    WHERE embedding.embedding_model = :embeddingModel
                      AND policy.active = TRUE
                      AND policy.effective_from <= :effectiveDate
                      AND (policy.effective_to IS NULL OR policy.effective_to >= :effectiveDate)
                      AND (policy.target_type = 'ALL' OR policy.target_type = :targetType)
                      AND (CAST(:policyType AS varchar) IS NULL
                           OR policy.policy_type = :policyType)
                    ORDER BY embedding.embedding <=> CAST(:queryEmbedding AS vector)
                    LIMIT :vectorCandidateLimit
                )
                SELECT policy.id AS policy_id,
                       chunk.id AS policy_chunk_id,
                       policy.policy_code,
                       policy.title AS policy_title,
                       policy.policy_type,
                       policy.target_type,
                       policy.version AS policy_version,
                       chunk.clause_code,
                       chunk.content,
                       chunk.content_hash,
                       policy.effective_from,
                       policy.effective_to,
                       (lower(policy.title) LIKE :keyword ESCAPE '!'
                         OR lower(chunk.content) LIKE :keyword ESCAPE '!') AS keyword_matched,
                       COALESCE(semantic.semantic_distance, 1.0) AS semantic_distance,
                       (CASE WHEN lower(policy.title) LIKE :keyword ESCAPE '!'
                                   OR lower(chunk.content) LIKE :keyword ESCAPE '!'
                              THEN 0.45 ELSE 0 END
                        + 0.55 * GREATEST(0, 1 - COALESCE(semantic.semantic_distance, 1.0)))
                         AS hybrid_score
                FROM moderation_policy_chunks chunk
                JOIN moderation_policies policy ON policy.id = chunk.policy_id
                LEFT JOIN semantic_candidates semantic ON semantic.policy_chunk_id = chunk.id
                WHERE policy.active = TRUE
                  AND policy.effective_from <= :effectiveDate
                  AND (policy.effective_to IS NULL OR policy.effective_to >= :effectiveDate)
                  AND (policy.target_type = 'ALL' OR policy.target_type = :targetType)
                  AND (CAST(:policyType AS varchar) IS NULL
                       OR policy.policy_type = :policyType)
                  AND (lower(policy.title) LIKE :keyword ESCAPE '!'
                       OR lower(chunk.content) LIKE :keyword ESCAPE '!'
                       OR semantic.policy_chunk_id IS NOT NULL)
                ORDER BY hybrid_score DESC, policy.policy_code, policy.version DESC,
                         chunk.chunk_order, chunk.id
                LIMIT :resultLimit
                """, params, CANDIDATE_MAPPER);

        return new Candidates(rows.stream().limit(limit).toList(), rows.size() > limit);
    }

    private void configureFilteredHnswSearch() {
        jdbc.getJdbcTemplate().execute("SET LOCAL hnsw.iterative_scan = 'strict_order'");
        jdbc.getJdbcTemplate().execute(
                "SET LOCAL hnsw.max_scan_tuples = '" + HNSW_MAX_SCAN_TUPLES + "'"
        );
    }

    public EmbeddingJobs findEmbeddingJobs(
            String embeddingModel,
            int limit,
            LocalDate effectiveDate
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("embeddingModel", embeddingModel);
        params.put("effectiveDate", effectiveDate);
        params.put("limit", limit);
        List<EmbeddingJob> jobs = jdbc.query("""
                SELECT policy.id AS policy_id,
                       chunk.id AS policy_chunk_id,
                       policy.policy_code,
                       policy.version AS policy_version,
                       chunk.clause_code,
                       chunk.content,
                       chunk.content_hash
                FROM moderation_policy_chunks chunk
                JOIN moderation_policies policy ON policy.id = chunk.policy_id
                LEFT JOIN moderation_policy_embeddings embedding
                  ON embedding.policy_chunk_id = chunk.id
                 AND embedding.embedding_model = :embeddingModel
                WHERE (
                        policy.active = TRUE
                        OR policy.version = (
                            SELECT MAX(latest.version)
                            FROM moderation_policies latest
                            WHERE latest.policy_code = policy.policy_code
                        )
                      )
                  AND (policy.effective_to IS NULL OR policy.effective_to >= :effectiveDate)
                  AND (embedding.policy_chunk_id IS NULL
                       OR embedding.content_hash <> chunk.content_hash)
                ORDER BY policy.policy_code, policy.version DESC, chunk.chunk_order, chunk.id
                LIMIT :limit
                """, params, EMBEDDING_JOB_MAPPER);
        return new EmbeddingJobs(jobs);
    }

    public void upsertEmbedding(
            long policyChunkId,
            String embeddingModel,
            String contentHash,
            List<Double> embedding
    ) {
        Map<String, Object> params = Map.of(
                "policyChunkId", policyChunkId,
                "embeddingModel", embeddingModel,
                "contentHash", contentHash,
                "embedding", vectorLiteral(embedding)
        );
        int updated = jdbc.update("""
                INSERT INTO moderation_policy_embeddings
                    (policy_chunk_id, embedding_model, embedding, content_hash, embedded_at)
                SELECT chunk.id, :embeddingModel, CAST(:embedding AS vector),
                       chunk.content_hash, CURRENT_TIMESTAMP
                FROM moderation_policy_chunks chunk
                WHERE chunk.id = :policyChunkId
                  AND chunk.content_hash = :contentHash
                ON CONFLICT (policy_chunk_id, embedding_model)
                DO UPDATE SET embedding = EXCLUDED.embedding,
                              content_hash = EXCLUDED.content_hash,
                              embedded_at = EXCLUDED.embedded_at
                """, params);
        if (updated != 1) {
            throw new BadRequestException("운영 정책 조항이 변경되었거나 존재하지 않습니다.");
        }
    }

    private Map<String, Object> commonParameters(
            ReportTargetType targetType,
            ModerationPolicyType policyType,
            String embeddingModel,
            LocalDate effectiveDate
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("targetType", targetType.name());
        params.put("policyType", policyType == null ? null : policyType.name());
        params.put("embeddingModel", embeddingModel);
        params.put("effectiveDate", effectiveDate);
        return params;
    }

    static String escapedKeyword(String keyword) {
        String normalized = keyword.strip().toLowerCase(Locale.ROOT);
        return "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    static String vectorLiteral(List<Double> embedding) {
        return embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
