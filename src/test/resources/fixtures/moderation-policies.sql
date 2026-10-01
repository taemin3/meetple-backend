INSERT INTO moderation_policies
    (id, policy_code, title, policy_type, target_type, effective_from, effective_to,
     active, version, created_at, updated_at)
VALUES
    (101, 'COMMUNITY-SPAM', '스팸 및 반복 홍보 금지', 'SPAM', 'ALL',
     DATE '2026-01-01', NULL, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (102, 'COMMUNITY-HARASSMENT', '괴롭힘 및 모욕 금지', 'ABUSE_OR_HARASSMENT',
     'CHAT_MESSAGE', DATE '2026-01-01', NULL, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (103, 'COMMUNITY-SAFETY', '위험한 모임 운영 금지', 'SAFETY', 'MEETING',
     DATE '2026-01-01', NULL, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO moderation_policy_chunks
    (id, policy_id, clause_code, chunk_order, content, content_hash, created_at, updated_at)
VALUES
    (1001, 101, 'SPAM-1', 0,
     '동일하거나 유사한 광고성 내용을 반복해서 게시하거나 전송해서는 안 됩니다.',
     '1111111111111111111111111111111111111111111111111111111111111111',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1002, 102, 'HARASSMENT-1', 0,
     '다른 회원을 모욕하거나 위협하거나 반복적으로 괴롭히는 메시지를 보내서는 안 됩니다.',
     '2222222222222222222222222222222222222222222222222222222222222222',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1003, 103, 'SAFETY-1', 0,
     '참가자의 안전을 현저하게 해칠 수 있는 방식으로 모임을 운영해서는 안 됩니다.',
     '3333333333333333333333333333333333333333333333333333333333333333',
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
