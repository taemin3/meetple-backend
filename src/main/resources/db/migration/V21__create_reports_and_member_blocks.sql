CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_member_id BIGINT NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    other_description VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_reports_reporter_member
        FOREIGN KEY (reporter_member_id) REFERENCES members (id),
    CONSTRAINT ck_reports_target_type
        CHECK (target_type IN ('MEMBER', 'MEETING', 'CHAT_MESSAGE')),
    CONSTRAINT ck_reports_reason
        CHECK (reason IN ('SPAM', 'ABUSE_OR_HARASSMENT', 'INAPPROPRIATE_CONTENT', 'FRAUD_OR_FALSE_INFORMATION', 'OTHER'))
);

CREATE INDEX idx_reports_reporter_created_at
    ON reports (reporter_member_id, created_at DESC);
CREATE INDEX idx_reports_target
    ON reports (target_type, target_id);

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
