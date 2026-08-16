ALTER TABLE meetings
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_meetings_deleted_at
    ON meetings (deleted_at);

CREATE TABLE image_deletion_tasks (
    id BIGSERIAL PRIMARY KEY,
    object_key VARCHAR(255) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_image_deletion_tasks_attempts_non_negative CHECK (attempts >= 0)
);

CREATE INDEX idx_image_deletion_tasks_next_attempt_at
    ON image_deletion_tasks (next_attempt_at, id);
