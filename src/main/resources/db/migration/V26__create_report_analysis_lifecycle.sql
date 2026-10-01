ALTER TABLE reports
    ADD COLUMN target_snapshot TEXT,
    ADD COLUMN target_snapshot_hash CHAR(64),
    ADD CONSTRAINT ck_reports_target_snapshot_pair CHECK (
        (target_snapshot IS NULL AND target_snapshot_hash IS NULL)
        OR (target_snapshot IS NOT NULL AND target_snapshot_hash IS NOT NULL)
    ),
    ADD CONSTRAINT ck_reports_target_snapshot_not_blank CHECK (
        target_snapshot IS NULL OR btrim(target_snapshot) <> ''
    ),
    ADD CONSTRAINT ck_reports_target_snapshot_hash CHECK (
        target_snapshot_hash IS NULL OR target_snapshot_hash ~ '^[0-9a-f]{64}$'
    );

CREATE TABLE report_analyses (
    report_id BIGINT PRIMARY KEY,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    report_type VARCHAR(50),
    risk_level VARCHAR(20),
    priority VARCHAR(20),
    summary VARCHAR(500),
    rationale VARCHAR(1000),
    confidence NUMERIC(5, 4),
    recommended_action VARCHAR(50),
    result_hash CHAR(64),
    failure_code VARCHAR(100),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_analyses_report
        FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE,
    CONSTRAINT ck_report_analyses_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'FAILED_RETRYABLE',
            'FAILED_PERMANENT'
        )
    ),
    CONSTRAINT ck_report_analyses_attempt_count_non_negative CHECK (attempt_count >= 0),
    CONSTRAINT ck_report_analyses_report_type CHECK (
        report_type IS NULL OR report_type IN (
            'SPAM',
            'ABUSE_OR_HARASSMENT',
            'INAPPROPRIATE_CONTENT',
            'FRAUD_OR_FALSE_INFORMATION',
            'SAFETY',
            'OTHER'
        )
    ),
    CONSTRAINT ck_report_analyses_risk_level CHECK (
        risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_report_analyses_priority CHECK (
        priority IS NULL OR priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
    ),
    CONSTRAINT ck_report_analyses_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    ),
    CONSTRAINT ck_report_analyses_recommended_action CHECK (
        recommended_action IS NULL OR recommended_action IN (
            'DISMISS',
            'WARNING',
            'SUSPEND_1_DAY',
            'SUSPEND_3_DAYS',
            'SUSPEND_7_DAYS',
            'PERMANENT_SUSPENSION',
            'FORCE_DELETE_MEETING',
            'MANUAL_REVIEW'
        )
    ),
    CONSTRAINT ck_report_analyses_result_hash CHECK (
        result_hash IS NULL OR result_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_report_analyses_failure_code CHECK (
        failure_code IS NULL OR failure_code ~ '^[A-Z][A-Z0-9_]{0,99}$'
    ),
    CONSTRAINT ck_report_analyses_completed_fields CHECK (
        status <> 'COMPLETED' OR (
            report_type IS NOT NULL
            AND risk_level IS NOT NULL
            AND priority IS NOT NULL
            AND summary IS NOT NULL
            AND rationale IS NOT NULL
            AND confidence IS NOT NULL
            AND recommended_action IS NOT NULL
            AND result_hash IS NOT NULL
            AND completed_at IS NOT NULL
            AND failure_code IS NULL
        )
    )
);

CREATE INDEX idx_report_analyses_status_updated
    ON report_analyses (status, updated_at);

CREATE TABLE report_analysis_policies (
    report_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    PRIMARY KEY (report_id, policy_id),
    CONSTRAINT fk_report_analysis_policies_analysis
        FOREIGN KEY (report_id) REFERENCES report_analyses (report_id) ON DELETE CASCADE,
    CONSTRAINT fk_report_analysis_policies_policy
        FOREIGN KEY (policy_id) REFERENCES moderation_policies (id)
);
