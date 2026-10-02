package com.meetple.backend.domain.moderation.policy;

import static com.meetple.backend.domain.moderation.policy.AdminModerationPolicyContracts.*;

import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AdminModerationPolicyRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AdminModerationPolicyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Page<PolicySummary> findAll(
            String policyCode,
            Boolean active,
            int page,
            int size,
            String embeddingModel
    ) {
        String normalizedCode = policyCode == null || policyCode.isBlank()
                ? null : "%" + policyCode.strip().toLowerCase() + "%";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("policyCode", normalizedCode, Types.VARCHAR)
                .addValue("active", active, Types.BOOLEAN)
                .addValue("embeddingModel", embeddingModel)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String conditions = """
                WHERE (:policyCode IS NULL OR lower(policy.policy_code) LIKE :policyCode)
                  AND (:active IS NULL OR policy.active = :active)
                """;
        List<PolicySummary> content = jdbc.query("""
                SELECT policy.id,
                       policy.policy_code,
                       policy.title,
                       policy.policy_type,
                       policy.target_type,
                       policy.effective_from,
                       policy.effective_to,
                       policy.active,
                       policy.version,
                       COUNT(chunk.id) AS clause_count,
                       COUNT(chunk.id) FILTER (
                           WHERE embedding.policy_chunk_id IS NULL
                              OR embedding.content_hash <> chunk.content_hash
                       ) AS missing_embedding_count,
                       policy.created_at,
                       policy.updated_at
                FROM moderation_policies policy
                LEFT JOIN moderation_policy_chunks chunk ON chunk.policy_id = policy.id
                LEFT JOIN moderation_policy_embeddings embedding
                  ON embedding.policy_chunk_id = chunk.id
                 AND embedding.embedding_model = :embeddingModel
                """ + conditions + """
                GROUP BY policy.id
                ORDER BY policy.policy_code, policy.version DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNum) -> new PolicySummary(
                rs.getLong("id"),
                rs.getString("policy_code"),
                rs.getString("title"),
                ModerationPolicyType.valueOf(rs.getString("policy_type")),
                ModerationPolicyTargetType.valueOf(rs.getString("target_type")),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class),
                rs.getBoolean("active"),
                rs.getInt("version"),
                rs.getInt("clause_count"),
                rs.getInt("missing_embedding_count"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class)
        ));
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM moderation_policies policy " + conditions,
                params,
                Long.class
        );
        return new PageImpl<>(content, PageRequest.of(page, size), total == null ? 0 : total);
    }

    public Optional<PolicyRow> findById(long policyId) {
        return findPolicy(policyId, false);
    }

    public Optional<PolicyRow> findByIdForUpdate(long policyId) {
        return findPolicy(policyId, true);
    }

    private Optional<PolicyRow> findPolicy(long policyId, boolean forUpdate) {
        List<PolicyRow> rows = jdbc.query("""
                SELECT id, policy_code, title, policy_type, target_type,
                       effective_from, effective_to, active, version, created_at, updated_at
                FROM moderation_policies
                WHERE id = :policyId
                """ + (forUpdate ? " FOR UPDATE" : ""), Map.of("policyId", policyId),
                (rs, rowNum) -> new PolicyRow(
                        rs.getLong("id"),
                        rs.getString("policy_code"),
                        rs.getString("title"),
                        ModerationPolicyType.valueOf(rs.getString("policy_type")),
                        ModerationPolicyTargetType.valueOf(rs.getString("target_type")),
                        rs.getObject("effective_from", LocalDate.class),
                        rs.getObject("effective_to", LocalDate.class),
                        rs.getBoolean("active"),
                        rs.getInt("version"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class)
                ));
        return rows.stream().findFirst();
    }

    public boolean existsByPolicyCode(String policyCode) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM moderation_policies WHERE policy_code = :policyCode
                )
                """, Map.of("policyCode", policyCode), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public int lockVersionsAndFindLatest(String policyCode) {
        List<Integer> versions = jdbc.query("""
                SELECT version
                FROM moderation_policies
                WHERE policy_code = :policyCode
                ORDER BY version
                FOR UPDATE
                """, Map.of("policyCode", policyCode),
                (rs, rowNum) -> rs.getInt("version"));
        return versions.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public long insertPolicy(
            String policyCode,
            String title,
            ModerationPolicyType policyType,
            ModerationPolicyTargetType targetType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            int version,
            LocalDateTime now
    ) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("policyCode", policyCode)
                .addValue("title", title)
                .addValue("policyType", policyType.name())
                .addValue("targetType", targetType.name())
                .addValue("effectiveFrom", effectiveFrom)
                .addValue("effectiveTo", effectiveTo)
                .addValue("version", version)
                .addValue("now", now);
        jdbc.update("""
                INSERT INTO moderation_policies (
                    policy_code, title, policy_type, target_type,
                    effective_from, effective_to, active, version, created_at, updated_at
                ) VALUES (
                    :policyCode, :title, :policyType, :targetType,
                    :effectiveFrom, :effectiveTo, FALSE, :version, :now, :now
                )
                """, params, keyHolder, new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("운영 정책 ID를 확인할 수 없습니다.");
        }
        return key.longValue();
    }

    public void insertClauses(long policyId, List<ClauseRow> clauses, LocalDateTime now) {
        MapSqlParameterSource[] batch = clauses.stream()
                .map(clause -> new MapSqlParameterSource()
                        .addValue("policyId", policyId)
                        .addValue("clauseCode", clause.clauseCode())
                        .addValue("chunkOrder", clause.chunkOrder())
                        .addValue("content", clause.content())
                        .addValue("contentHash", clause.contentHash())
                        .addValue("now", now))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO moderation_policy_chunks (
                    policy_id, clause_code, chunk_order, content, content_hash, created_at, updated_at
                ) VALUES (
                    :policyId, :clauseCode, :chunkOrder, :content, :contentHash, :now, :now
                )
                """, batch);
    }

    public List<PolicyClause> findClauses(long policyId, String embeddingModel) {
        return jdbc.query("""
                SELECT chunk.id,
                       chunk.clause_code,
                       chunk.chunk_order,
                       chunk.content,
                       chunk.content_hash,
                       CASE WHEN embedding.policy_chunk_id IS NOT NULL
                                  AND embedding.content_hash = chunk.content_hash
                            THEN TRUE ELSE FALSE END AS embedded
                FROM moderation_policy_chunks chunk
                LEFT JOIN moderation_policy_embeddings embedding
                  ON embedding.policy_chunk_id = chunk.id
                 AND embedding.embedding_model = :embeddingModel
                WHERE chunk.policy_id = :policyId
                ORDER BY chunk.chunk_order, chunk.id
                """, new MapSqlParameterSource()
                .addValue("policyId", policyId)
                .addValue("embeddingModel", embeddingModel),
                (rs, rowNum) -> new PolicyClause(
                        rs.getLong("id"),
                        rs.getString("clause_code"),
                        rs.getInt("chunk_order"),
                        rs.getString("content"),
                        rs.getString("content_hash"),
                        rs.getBoolean("embedded")
                ));
    }

    public int countMissingEmbeddings(long policyId, String embeddingModel) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM moderation_policy_chunks chunk
                LEFT JOIN moderation_policy_embeddings embedding
                  ON embedding.policy_chunk_id = chunk.id
                 AND embedding.embedding_model = :embeddingModel
                WHERE chunk.policy_id = :policyId
                  AND (embedding.policy_chunk_id IS NULL
                       OR embedding.content_hash <> chunk.content_hash)
                """, new MapSqlParameterSource()
                .addValue("policyId", policyId)
                .addValue("embeddingModel", embeddingModel), Integer.class);
        return count == null ? 0 : count;
    }

    public List<Long> deactivateOtherVersions(long policyId, String policyCode, LocalDateTime now) {
        return jdbc.query("""
                UPDATE moderation_policies
                SET active = FALSE, updated_at = :now
                WHERE policy_code = :policyCode
                  AND id <> :policyId
                  AND active = TRUE
                RETURNING id
                """, new MapSqlParameterSource()
                .addValue("policyId", policyId)
                .addValue("policyCode", policyCode)
                .addValue("now", now),
                (rs, rowNum) -> rs.getLong("id"));
    }

    public void updateActivation(long policyId, boolean active, LocalDateTime now) {
        int updated = jdbc.update("""
                UPDATE moderation_policies
                SET active = :active, updated_at = :now
                WHERE id = :policyId
                """, new MapSqlParameterSource()
                .addValue("policyId", policyId)
                .addValue("active", active)
                .addValue("now", now));
        if (updated != 1) {
            throw new IllegalStateException("운영 정책 활성 상태를 변경할 수 없습니다.");
        }
    }

    public void insertAudit(
            long policyId,
            long administratorMemberId,
            PolicyAuditAction action,
            LocalDateTime now
    ) {
        jdbc.update("""
                INSERT INTO moderation_policy_audits (
                    policy_id, administrator_member_id, action_type, created_at
                ) VALUES (
                    :policyId, :administratorMemberId, :actionType, :now
                )
                """, new MapSqlParameterSource()
                .addValue("policyId", policyId)
                .addValue("administratorMemberId", administratorMemberId)
                .addValue("actionType", action.name())
                .addValue("now", now));
    }

    public List<PolicyAudit> findAudits(long policyId) {
        return jdbc.query("""
                SELECT audit.id,
                       audit.administrator_member_id,
                       administrator.nickname,
                       audit.action_type,
                       audit.created_at
                FROM moderation_policy_audits audit
                JOIN members administrator ON administrator.id = audit.administrator_member_id
                WHERE audit.policy_id = :policyId
                ORDER BY audit.created_at DESC, audit.id DESC
                """, Map.of("policyId", policyId), (rs, rowNum) -> new PolicyAudit(
                rs.getLong("id"),
                rs.getLong("administrator_member_id"),
                rs.getString("nickname"),
                PolicyAuditAction.valueOf(rs.getString("action_type")),
                rs.getObject("created_at", LocalDateTime.class)
        ));
    }

    public record PolicyRow(
            long policyId,
            String policyCode,
            String title,
            ModerationPolicyType policyType,
            ModerationPolicyTargetType targetType,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            int version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ClauseRow(String clauseCode, int chunkOrder, String content, String contentHash) {
    }
}
