CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    room_sequence BIGINT NOT NULL,
    client_message_id UUID NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chat_messages_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_id) REFERENCES members (id),
    CONSTRAINT uk_chat_messages_room_sequence
        UNIQUE (meeting_id, room_sequence),
    CONSTRAINT uk_chat_messages_client_message
        UNIQUE (meeting_id, sender_id, client_message_id)
);

CREATE INDEX idx_chat_messages_meeting_sequence
    ON chat_messages (meeting_id, room_sequence DESC);

CREATE TABLE chat_read_states (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    last_read_sequence BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chat_read_states_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_read_states_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT uk_chat_read_states_meeting_member
        UNIQUE (meeting_id, member_id)
);

CREATE INDEX idx_chat_read_states_member
    ON chat_read_states (member_id);
