CREATE TABLE push_event_recipient_decisions (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    member_id BIGINT NOT NULL,
    suppressed BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_push_event_recipient_decisions_event_member
        UNIQUE (event_id, member_id)
);

CREATE INDEX idx_push_event_recipient_decisions_event
    ON push_event_recipient_decisions (event_id);
