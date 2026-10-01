CREATE TABLE report_analysis_evidence (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    evidence_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_analysis_evidence_report
        FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE,
    CONSTRAINT uk_report_analysis_evidence_source
        UNIQUE (report_id, evidence_type, source_id),
    CONSTRAINT ck_report_analysis_evidence_type CHECK (
        evidence_type IN ('MEMBER', 'MEETING', 'CHAT_MESSAGE')
    ),
    CONSTRAINT ck_report_analysis_evidence_content_not_blank CHECK (btrim(content) <> ''),
    CONSTRAINT ck_report_analysis_evidence_content_hash CHECK (
        content_hash ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_report_analysis_evidence_report
    ON report_analysis_evidence (report_id, id);

CREATE TABLE moderation_policy_retrievals (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    query_embedding_model VARCHAR(100) NOT NULL,
    keyword VARCHAR(200) NOT NULL,
    requested_policy_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_moderation_policy_retrievals_report
        FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE,
    CONSTRAINT ck_moderation_policy_retrievals_keyword_not_blank CHECK (btrim(keyword) <> ''),
    CONSTRAINT ck_moderation_policy_retrievals_policy_type CHECK (
        requested_policy_type IS NULL OR requested_policy_type IN (
            'SPAM',
            'ABUSE_OR_HARASSMENT',
            'INAPPROPRIATE_CONTENT',
            'FRAUD_OR_FALSE_INFORMATION',
            'SAFETY',
            'GENERAL'
        )
    )
);

CREATE INDEX idx_moderation_policy_retrievals_report
    ON moderation_policy_retrievals (report_id, created_at DESC);

CREATE TABLE moderation_policy_retrieval_items (
    retrieval_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    policy_chunk_id BIGINT NOT NULL,
    result_rank INTEGER NOT NULL,
    content_hash CHAR(64) NOT NULL,
    PRIMARY KEY (retrieval_id, policy_chunk_id),
    CONSTRAINT fk_moderation_policy_retrieval_items_retrieval
        FOREIGN KEY (retrieval_id) REFERENCES moderation_policy_retrievals (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_moderation_policy_retrieval_items_policy
        FOREIGN KEY (policy_id) REFERENCES moderation_policies (id),
    CONSTRAINT fk_moderation_policy_retrieval_items_chunk
        FOREIGN KEY (policy_chunk_id) REFERENCES moderation_policy_chunks (id),
    CONSTRAINT uk_moderation_policy_retrieval_items_rank
        UNIQUE (retrieval_id, result_rank),
    CONSTRAINT ck_moderation_policy_retrieval_items_rank_positive CHECK (result_rank > 0),
    CONSTRAINT ck_moderation_policy_retrieval_items_content_hash CHECK (
        content_hash ~ '^[0-9a-f]{64}$'
    )
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
    policy_retrieval_id BIGINT,
    result_hash CHAR(64),
    failure_code VARCHAR(100),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_analyses_report
        FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_report_analyses_policy_retrieval
        FOREIGN KEY (policy_retrieval_id) REFERENCES moderation_policy_retrievals (id),
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
            AND policy_retrieval_id IS NOT NULL
            AND result_hash IS NOT NULL
            AND completed_at IS NOT NULL
            AND failure_code IS NULL
        )
    )
);

CREATE INDEX idx_report_analyses_status_updated
    ON report_analyses (status, updated_at);

CREATE TABLE report_analysis_evidence_selections (
    report_id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    PRIMARY KEY (report_id, evidence_id),
    CONSTRAINT fk_report_analysis_evidence_selections_analysis
        FOREIGN KEY (report_id) REFERENCES report_analyses (report_id) ON DELETE CASCADE,
    CONSTRAINT fk_report_analysis_evidence_selections_evidence
        FOREIGN KEY (evidence_id) REFERENCES report_analysis_evidence (id)
);

CREATE TABLE report_analysis_policy_selections (
    report_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    PRIMARY KEY (report_id, policy_id),
    CONSTRAINT fk_report_analysis_policy_selections_analysis
        FOREIGN KEY (report_id) REFERENCES report_analyses (report_id) ON DELETE CASCADE,
    CONSTRAINT fk_report_analysis_policy_selections_policy
        FOREIGN KEY (policy_id) REFERENCES moderation_policies (id)
);
