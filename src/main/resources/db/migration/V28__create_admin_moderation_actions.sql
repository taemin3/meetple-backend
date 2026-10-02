ALTER TABLE members
    ADD COLUMN suspended_until TIMESTAMP,
    ADD COLUMN permanently_suspended_at TIMESTAMP,
    ADD COLUMN suspension_report_id BIGINT,
    ADD CONSTRAINT fk_members_suspension_report
        FOREIGN KEY (suspension_report_id) REFERENCES reports (id),
    ADD CONSTRAINT ck_members_single_suspension_type CHECK (
        suspended_until IS NULL OR permanently_suspended_at IS NULL
    ),
    ADD CONSTRAINT ck_members_suspension_source CHECK (
        (suspended_until IS NULL
            AND permanently_suspended_at IS NULL
            AND suspension_report_id IS NULL)
        OR
        ((suspended_until IS NOT NULL OR permanently_suspended_at IS NOT NULL)
            AND suspension_report_id IS NOT NULL)
    );

ALTER TABLE meetings
    ADD COLUMN moderation_deleted_by_report_id BIGINT,
    ADD CONSTRAINT fk_meetings_moderation_deleted_by_report
        FOREIGN KEY (moderation_deleted_by_report_id) REFERENCES reports (id);

ALTER TABLE reports
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN resolution_action VARCHAR(40),
    ADD COLUMN resolved_by_member_id BIGINT,
    ADD COLUMN resolved_at TIMESTAMP,
    ADD CONSTRAINT fk_reports_resolved_by_member
        FOREIGN KEY (resolved_by_member_id) REFERENCES members (id),
    ADD CONSTRAINT ck_reports_review_status CHECK (
        review_status IN ('PENDING', 'RESOLVED')
    ),
    ADD CONSTRAINT ck_reports_resolution_action CHECK (
        resolution_action IS NULL OR resolution_action IN (
            'DISMISS',
            'WARNING',
            'SUSPEND_1_DAY',
            'SUSPEND_3_DAYS',
            'SUSPEND_7_DAYS',
            'PERMANENT_SUSPENSION',
            'FORCE_DELETE_MEETING'
        )
    ),
    ADD CONSTRAINT ck_reports_resolution_fields CHECK (
        (review_status = 'PENDING'
            AND resolution_action IS NULL
            AND resolved_by_member_id IS NULL
            AND resolved_at IS NULL)
        OR
        (review_status = 'RESOLVED'
            AND resolution_action IS NOT NULL
            AND resolved_by_member_id IS NOT NULL
            AND resolved_at IS NOT NULL)
    );

CREATE INDEX idx_reports_review_created_at
    ON reports (review_status, created_at DESC);

CREATE TABLE moderation_actions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    administrator_member_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    target_member_id BIGINT,
    target_meeting_id BIGINT,
    reason VARCHAR(500) NOT NULL,
    effective_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_moderation_actions_report
        FOREIGN KEY (report_id) REFERENCES reports (id),
    CONSTRAINT fk_moderation_actions_administrator
        FOREIGN KEY (administrator_member_id) REFERENCES members (id),
    CONSTRAINT fk_moderation_actions_target_member
        FOREIGN KEY (target_member_id) REFERENCES members (id),
    CONSTRAINT ck_moderation_actions_type CHECK (
        action_type IN (
            'DISMISS',
            'WARNING',
            'SUSPEND_1_DAY',
            'SUSPEND_3_DAYS',
            'SUSPEND_7_DAYS',
            'PERMANENT_SUSPENSION',
            'FORCE_DELETE_MEETING',
            'RELEASE_SUSPENSION',
            'RESTORE_MEETING'
        )
    ),
    CONSTRAINT ck_moderation_actions_reason_not_blank CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_moderation_actions_effective_until CHECK (
        (action_type IN ('SUSPEND_1_DAY', 'SUSPEND_3_DAYS', 'SUSPEND_7_DAYS')
            AND effective_until IS NOT NULL)
        OR
        (action_type NOT IN ('SUSPEND_1_DAY', 'SUSPEND_3_DAYS', 'SUSPEND_7_DAYS')
            AND effective_until IS NULL)
    )
);

CREATE INDEX idx_moderation_actions_report_created_at
    ON moderation_actions (report_id, created_at DESC, id DESC);

CREATE INDEX idx_moderation_actions_target_member_created_at
    ON moderation_actions (target_member_id, created_at DESC)
    WHERE target_member_id IS NOT NULL;
