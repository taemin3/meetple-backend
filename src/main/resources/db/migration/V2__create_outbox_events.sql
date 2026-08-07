CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deduplication_key VARCHAR(255) NOT NULL,
    CONSTRAINT uk_outbox_events_deduplication_key UNIQUE (deduplication_key),
    CONSTRAINT ck_outbox_events_schema_version_positive CHECK (schema_version > 0)
);

CREATE INDEX idx_outbox_events_occurred_at
    ON outbox_events (occurred_at);
