INSERT INTO moderation_policies (
    policy_code, title, policy_type, target_type,
    effective_from, effective_to, active, version, created_at, updated_at
)
VALUES
    ('COMMUNITY-SPAM', '스팸 및 반복 홍보 금지', 'SPAM', 'ALL',
     DATE '2026-10-01', NULL, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('COMMUNITY-HARASSMENT', '괴롭힘 및 모욕 금지', 'ABUSE_OR_HARASSMENT', 'ALL',
     DATE '2026-10-01', NULL, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('COMMUNITY-CONTENT', '부적절한 콘텐츠 금지', 'INAPPROPRIATE_CONTENT', 'ALL',
     DATE '2026-10-01', NULL, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('COMMUNITY-FRAUD', '허위 정보 및 사기 금지', 'FRAUD_OR_FALSE_INFORMATION', 'ALL',
     DATE '2026-10-01', NULL, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('COMMUNITY-SAFETY', '안전한 모임 운영', 'SAFETY', 'MEETING',
     DATE '2026-10-01', NULL, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('COMMUNITY-PRIVACY', '개인정보 보호', 'GENERAL', 'ALL',
     DATE '2026-10-01', NULL, FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (policy_code, version) DO NOTHING;

INSERT INTO moderation_policy_chunks (
    policy_id, clause_code, chunk_order, content, content_hash, created_at, updated_at
)
SELECT policy.id, seed.clause_code, seed.chunk_order, seed.content, seed.content_hash,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    VALUES
        ('COMMUNITY-SPAM', 'SPAM-1', 0,
         '동일하거나 유사한 광고성 내용을 반복해서 게시하거나 전송해서는 안 됩니다.',
         '6af25c64ef45abbfce522a3e0b40172949fa60dd976936b7bb486dc4576ed655'),
        ('COMMUNITY-SPAM', 'SPAM-2', 1,
         '자동화된 수단으로 동일한 링크나 홍보 문구를 여러 회원에게 반복 전송해서는 안 됩니다.',
         '0e4360f26449df0a2a869b2d96545bf6b966779d6868f9bb55eca382ad7c9b78'),
        ('COMMUNITY-HARASSMENT', 'HARASSMENT-1', 0,
         '다른 회원을 모욕하거나 위협하거나 반복적으로 괴롭히는 표현을 사용해서는 안 됩니다.',
         'd2d8acb2afcb9eda13db6e7d33567ca7f15ed9a2a51c95e9bc2d9f4d14322c51'),
        ('COMMUNITY-CONTENT', 'CONTENT-1', 0,
         '성적이거나 폭력적인 내용을 상대방의 동의 없이 게시하거나 전송해서는 안 됩니다.',
         'f6013d80e9999e55c8db106ce8d20a9ba126c75665fc6696fbbca34c098c0000'),
        ('COMMUNITY-FRAUD', 'FRAUD-1', 0,
         '상품, 비용, 일정 또는 참여 조건을 허위로 안내해 다른 회원을 속여서는 안 됩니다.',
         '3dd4fb6be7c0ed8964d235872da19e1ccec2a3bf88402cb5bc782b3c8ba43079'),
        ('COMMUNITY-SAFETY', 'SAFETY-1', 0,
         '참가자의 안전을 현저하게 해칠 수 있는 방식으로 모임을 운영해서는 안 됩니다.',
         'd38019f9c2c0a749a91079546cfde1e60a2a720f309f9ec6da3bb253969471c3'),
        ('COMMUNITY-SAFETY', 'SAFETY-2', 1,
         '오프라인 모임의 위험 요소와 필수 준비 사항을 참가자에게 사전에 알려야 합니다.',
         'c35a0fa726be15df132865dce614a5c5cb130fe03347309e7c301d675745f704'),
        ('COMMUNITY-PRIVACY', 'PRIVACY-1', 0,
         '타인의 개인정보를 동의 없이 공개하거나 서비스 목적과 다르게 이용해서는 안 됩니다.',
         '0a098531ca1c148fefae112bb12e7a9ab2a7d08cb4cde51496930f024bce3957')
) AS seed(policy_code, clause_code, chunk_order, content, content_hash)
JOIN moderation_policies policy
  ON policy.policy_code = seed.policy_code
 AND policy.version = 1
ON CONFLICT (policy_id, clause_code) DO NOTHING;
