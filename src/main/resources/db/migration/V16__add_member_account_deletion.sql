ALTER TABLE members
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_members_deleted_at
    ON members (deleted_at);
