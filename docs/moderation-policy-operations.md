# 운영 정책 관리와 임베딩 동기화

운영 정책 원문은 감사 가능성을 위해 제자리에서 수정하지 않는다. 수정이 필요하면 관리자 API로 새 버전을 만들고, 임베딩 동기화 후 새 버전을 활성화한다.

## 관리 순서

1. `POST /api/v1/admin/moderation-policies`로 새 정책을 등록하거나 `POST /api/v1/admin/moderation-policies/{policyId}/versions`로 새 버전을 만든다.
2. 생성된 정책은 비활성 상태이며 각 조항은 `text-embedding-3-small` 임베딩이 필요한 상태다.
3. AI 서버와 Spring Backend를 실행한 뒤 아래 스크립트로 누락·stale 임베딩을 동기화한다.
4. `GET /api/v1/admin/moderation-policies/{policyId}`의 `missingEmbeddingCount`가 0인지 확인한다.
5. `PATCH /api/v1/admin/moderation-policies/{policyId}/activation`에 `{ "active": true }`를 보내 활성화한다.

활성화 시 같은 `policyCode`의 기존 활성 버전은 같은 트랜잭션에서 비활성화된다. 임베딩이 하나라도 없거나 원문 해시와 다르면 활성화 요청은 거절된다.

## 임베딩 동기화 실행

서비스 키를 명령 인자나 로그에 남기지 않고 환경변수로 전달한다.

```powershell
$env:AI_MODERATION_SERVICE_TOKEN = "로컬 또는 운영 서비스 키"
.\scripts\Sync-ModerationPolicyEmbeddings.ps1 -AiBaseUrl http://127.0.0.1:8001
```

스크립트는 한 번에 최대 100개씩 AI 서버의 `POST /v1/moderation/policies/embeddings/sync`를 반복 호출한다. AI 서버는 Spring에서 최신 정책 버전의 누락·stale 조항을 조회하고, 임베딩 생성 후 `contentHash`가 여전히 일치할 때만 저장한다.

## 초기 정책

`V30__seed_initial_moderation_policies.sql`은 개인정보가 없는 기본 정책 6개와 조항 8개를 비활성 상태로 입력한다. 문구를 검토하고 임베딩을 동기화한 후 필요한 정책만 관리자 API로 활성화한다. 운영 DB에 직접 `UPDATE`하여 활성화하지 않는다.
