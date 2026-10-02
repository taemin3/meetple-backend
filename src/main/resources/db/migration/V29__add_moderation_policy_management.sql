WITH ranked_active_policies AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY policy_code
               ORDER BY version DESC, id DESC
           ) AS active_rank
    FROM moderation_policies
    WHERE active = TRUE
)
UPDATE moderation_policies policy
SET active = FALSE,
    updated_at = CURRENT_TIMESTAMP
FROM ranked_active_policies ranked
WHERE policy.id = ranked.id
  AND ranked.active_rank > 1;

CREATE UNIQUE INDEX uk_moderation_policies_single_active_code
    ON moderation_policies (policy_code)
    WHERE active = TRUE;

CREATE TABLE moderation_policy_audits (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    administrator_member_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_moderation_policy_audits_policy
        FOREIGN KEY (policy_id) REFERENCES moderation_policies (id) ON DELETE CASCADE,
    CONSTRAINT fk_moderation_policy_audits_administrator
        FOREIGN KEY (administrator_member_id) REFERENCES members (id),
    CONSTRAINT ck_moderation_policy_audits_action CHECK (
        action_type IN ('CREATED', 'VERSION_CREATED', 'ACTIVATED', 'DEACTIVATED')
    )
);

CREATE INDEX idx_moderation_policy_audits_policy_created_at
    ON moderation_policy_audits (policy_id, created_at DESC, id DESC);
