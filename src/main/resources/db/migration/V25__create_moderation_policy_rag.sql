CREATE TABLE moderation_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_code VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    policy_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_moderation_policies_code_version UNIQUE (policy_code, version),
    CONSTRAINT ck_moderation_policies_policy_type CHECK (
        policy_type IN (
            'SPAM',
            'ABUSE_OR_HARASSMENT',
            'INAPPROPRIATE_CONTENT',
            'FRAUD_OR_FALSE_INFORMATION',
            'SAFETY',
            'GENERAL'
        )
    ),
    CONSTRAINT ck_moderation_policies_target_type CHECK (
        target_type IN ('ALL', 'MEMBER', 'MEETING', 'CHAT_MESSAGE')
    ),
    CONSTRAINT ck_moderation_policies_version_positive CHECK (version > 0),
    CONSTRAINT ck_moderation_policies_effective_range CHECK (
        effective_to IS NULL OR effective_to >= effective_from
    )
);

CREATE INDEX idx_moderation_policies_active_target_type
    ON moderation_policies (active, target_type, policy_type, effective_from, effective_to);

CREATE INDEX idx_moderation_policies_title_bigm
    ON moderation_policies
    USING gin (lower(title) gin_bigm_ops);

CREATE TABLE moderation_policy_chunks (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    clause_code VARCHAR(100) NOT NULL,
    chunk_order INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_moderation_policy_chunks_policy
        FOREIGN KEY (policy_id) REFERENCES moderation_policies (id) ON DELETE CASCADE,
    CONSTRAINT uk_moderation_policy_chunks_clause UNIQUE (policy_id, clause_code),
    CONSTRAINT uk_moderation_policy_chunks_order UNIQUE (policy_id, chunk_order),
    CONSTRAINT ck_moderation_policy_chunks_order_non_negative CHECK (chunk_order >= 0),
    CONSTRAINT ck_moderation_policy_chunks_content_not_blank CHECK (btrim(content) <> ''),
    CONSTRAINT ck_moderation_policy_chunks_content_hash CHECK (
        content_hash ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_moderation_policy_chunks_content_bigm
    ON moderation_policy_chunks
    USING gin (lower(content) gin_bigm_ops);

CREATE TABLE moderation_policy_embeddings (
    policy_chunk_id BIGINT NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    embedding vector(1536) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    embedded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (policy_chunk_id, embedding_model),
    CONSTRAINT fk_moderation_policy_embeddings_chunk
        FOREIGN KEY (policy_chunk_id) REFERENCES moderation_policy_chunks (id) ON DELETE CASCADE,
    CONSTRAINT ck_moderation_policy_embeddings_content_hash CHECK (
        content_hash ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_moderation_policy_embeddings_model
    ON moderation_policy_embeddings (embedding_model);

CREATE INDEX idx_moderation_policy_embeddings_embedding_hnsw
    ON moderation_policy_embeddings
    USING hnsw (embedding vector_cosine_ops);

ANALYZE moderation_policies;
ANALYZE moderation_policy_chunks;
