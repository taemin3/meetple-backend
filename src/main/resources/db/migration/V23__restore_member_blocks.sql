CREATE TABLE member_blocks (
    id BIGSERIAL PRIMARY KEY,
    blocker_member_id BIGINT NOT NULL,
    blocked_member_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_member_blocks_blocker
        FOREIGN KEY (blocker_member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_blocks_blocked
        FOREIGN KEY (blocked_member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT uk_member_blocks_relationship
        UNIQUE (blocker_member_id, blocked_member_id),
    CONSTRAINT ck_member_blocks_not_self
        CHECK (blocker_member_id <> blocked_member_id)
);

CREATE INDEX idx_member_blocks_blocker_created_at
    ON member_blocks (blocker_member_id, created_at DESC);
CREATE INDEX idx_member_blocks_blocked
    ON member_blocks (blocked_member_id);
