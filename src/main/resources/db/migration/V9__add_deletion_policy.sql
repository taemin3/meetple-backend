ALTER TABLE meetings
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_meetings_deleted_at
    ON meetings (deleted_at);
