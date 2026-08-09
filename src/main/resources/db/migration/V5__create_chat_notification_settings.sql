CREATE TABLE chat_notification_settings (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chat_notification_settings_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_notification_settings_member
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT uk_chat_notification_settings_meeting_member
        UNIQUE (meeting_id, member_id)
);

CREATE INDEX idx_chat_notification_settings_member
    ON chat_notification_settings (member_id);
