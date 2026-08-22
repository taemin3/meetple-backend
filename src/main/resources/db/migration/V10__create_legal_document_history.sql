CREATE TABLE legal_documents (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    version VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    effective_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_legal_documents_type_version UNIQUE (type, version)
);

CREATE INDEX idx_legal_documents_effective_at
    ON legal_documents (effective_at DESC);

CREATE TABLE member_legal_records (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    legal_document_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_member_legal_records_member
        FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_legal_records_document
        FOREIGN KEY (legal_document_id) REFERENCES legal_documents (id),
    CONSTRAINT uk_member_legal_records_member_document
        UNIQUE (member_id, legal_document_id)
);

CREATE INDEX idx_member_legal_records_member_recorded
    ON member_legal_records (member_id, recorded_at DESC);

INSERT INTO legal_documents (
    type, version, title, content, effective_at, created_at, updated_at
) VALUES
(
    'SERVICE_TERMS',
    '2026-08-22',
    '서비스 이용약관',
    '밋플 서비스는 사용자가 모임을 만들고 참여할 수 있도록 지원합니다. 사용자는 타인의 권리를 침해하거나 서비스 운영을 방해해서는 안 되며, 운영정책을 위반한 콘텐츠와 계정은 제한될 수 있습니다. 회사는 안정적인 서비스 제공을 위해 기능을 변경할 수 있고 중요한 변경은 서비스 내에서 안내합니다.',
    TIMESTAMP '2026-08-22 00:00:00',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'PRIVACY_POLICY',
    '2026-08-22',
    '개인정보 처리방침',
    '밋플은 회원가입과 서비스 제공을 위해 이메일, 비밀번호의 암호화 값, 닉네임을 필수로 처리하며 프로필 사진과 한줄 소개는 선택적으로 처리합니다. 개인정보는 회원 탈퇴 시까지 보관하되 관계 법령에 따라 보존할 의무가 있는 경우 해당 기간 동안 분리 보관합니다. 사용자는 자신의 개인정보를 조회·수정하고 회원 탈퇴를 요청할 수 있습니다.',
    TIMESTAMP '2026-08-22 00:00:00',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'AGE_14_CONFIRMATION',
    '2026-08-22',
    '만 14세 이상 확인',
    '회원가입을 진행하는 사용자는 만 14세 이상임을 확인합니다.',
    TIMESTAMP '2026-08-22 00:00:00',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
