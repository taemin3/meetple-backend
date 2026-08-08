CREATE TABLE push_event_deliveries (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    device_token_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error_code VARCHAR(100),
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_push_event_deliveries_event_device
        UNIQUE (event_id, device_token_id),
    CONSTRAINT ck_push_event_deliveries_status
        CHECK (status IN ('PENDING', 'SENT', 'INVALID_TOKEN', 'FAILED')),
    CONSTRAINT ck_push_event_deliveries_attempts_non_negative
        CHECK (attempts >= 0)
);

CREATE INDEX idx_push_event_deliveries_status_updated_at
    ON push_event_deliveries (status, updated_at);
