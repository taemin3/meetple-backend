CREATE TABLE chat_room_sequences (
    meeting_id BIGINT PRIMARY KEY,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_chat_room_sequences_meeting
        FOREIGN KEY (meeting_id) REFERENCES meetings (id) ON DELETE CASCADE,
    CONSTRAINT ck_chat_room_sequences_non_negative
        CHECK (last_sequence >= 0)
);

INSERT INTO chat_room_sequences (meeting_id, last_sequence)
SELECT m.id, COALESCE(MAX(cm.room_sequence), 0)
FROM meetings m
LEFT JOIN chat_messages cm ON cm.meeting_id = m.id
GROUP BY m.id;
