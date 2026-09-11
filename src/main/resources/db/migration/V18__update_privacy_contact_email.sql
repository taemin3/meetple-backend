INSERT INTO legal_documents (
    type, version, title, content, effective_at, created_at, updated_at
)
SELECT
    type,
    '2026-09-12.1',
    title,
    REPLACE(content, 'support@meetple.shop', 'meetple99@gmail.com'),
    TIMESTAMP '2026-09-12 00:01:00',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM legal_documents
WHERE type = 'PRIVACY_POLICY'
  AND version = '2026-09-12';
