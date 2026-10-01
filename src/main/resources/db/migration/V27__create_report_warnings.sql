CREATE TABLE report_warnings (
    report_id BIGINT PRIMARY KEY,
    target_member_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_warnings_report
        FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_report_warnings_target_member
        FOREIGN KEY (target_member_id) REFERENCES members (id)
);

CREATE INDEX idx_report_warnings_target_created_at
    ON report_warnings (target_member_id, created_at DESC);
