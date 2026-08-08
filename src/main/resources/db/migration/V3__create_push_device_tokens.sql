CREATE TABLE push_device_tokens (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    token VARCHAR(4096) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_push_device_tokens_member
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT uk_push_device_tokens_device_id UNIQUE (device_id),
    CONSTRAINT uk_push_device_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_push_device_tokens_platform
        CHECK (platform IN ('ANDROID', 'IOS'))
);

CREATE INDEX idx_push_device_tokens_member
    ON push_device_tokens (member_id);
