CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE meeting_embeddings (
    meeting_id BIGINT PRIMARY KEY,
    embedding vector(1536) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    embedded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_meeting_embeddings_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE,
    CONSTRAINT chk_meeting_embeddings_content_hash
        CHECK (content_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_meeting_embeddings_model
    ON meeting_embeddings (embedding_model);

CREATE INDEX idx_meeting_embeddings_embedding_hnsw
    ON meeting_embeddings
    USING hnsw (embedding vector_cosine_ops);
