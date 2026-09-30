# 작업 로그: feat/hybrid-meeting-search

## 기본 정보

- 날짜: 2026-10-01
- 브랜치: `feat/hybrid-meeting-search`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- AI 서버가 만든 질문 임베딩을 Spring 내부 검색 API와 pgvector 후보 검색에 연결한다.

## 작업 목표

- 내부 검색 API에서 선택적인 1536차원 질문 임베딩을 안전하게 검증한다.
- 기존 날짜·시간·거리·권한 조건을 유지하면서 키워드와 벡터 유사도를 결합한다.
- 임베딩이 아직 없는 모임도 키워드가 맞으면 검색 결과에 남긴다.

## 작업 흐름

1. 공개 응답용 `Filters`와 분리된 내부 API용 `ToolSearchRequest`를 추가한다.
2. 질문 임베딩과 모델 식별자 쌍, 차원, float4 범위, 0 벡터 여부를 검증한다.
3. 같은 모델의 HNSW 상위 후보와 전체 키워드 후보를 합쳐 점수를 계산한다.
4. 최종 추천 ID의 현재 권한·상태·구조 조건과 원문을 별도 조회로 재검증한다.
5. 실제 pgvector PostgreSQL 컨테이너에서 의미 후보와 키워드 후보의 결합 순서를 검증한다.

## 사용한 도구

- `rg`
- `apply_patch`
- Gradle
- Testcontainers

## 실행한 주요 명령

```powershell
.\gradlew.bat test --tests "com.meetple.backend.domain.ai.AiSearchControllerTest" --tests "com.meetple.backend.domain.ai.AiMeetingSearchRepositoryTest"
.\gradlew.bat test
```

## 변경 파일 요약

- `AiSearchContracts`: 내부 검색 요청 계약 추가
- `AiSearchToolController`: 질문 임베딩 검증과 Repository 전달
- `AiMeetingSearchRepository`: 모델별 HNSW 후보 제한, 키워드 45%와 코사인 유사도 55%의 하이브리드 정렬, 추천 ID 재검증 조회
- AI 검색 테스트: HTTP 계약, 임베딩 검증과 실제 pgvector 정렬 검증
- `docs/AI_SEARCH.md`: 현재 검색 방식과 남은 작업 명시

## 검증

- AI Controller, pgvector Repository와 최종 응답 검증 집중 테스트 17개 성공
- Testcontainers에서 `meeting_embeddings.embedding <=> queryEmbedding` 코사인 검색 실행 확인
- 전체 테스트 513개 성공, 실패·스킵 없음

## 이슈와 결정

- 공개 응답의 `Filters`에 1536개 값을 노출하지 않도록 내부 요청 record를 분리했다.
- 임베딩 저장이 끝나지 않은 개발 단계에서도 기존 키워드 검색 결과를 유지한다.
- AI 서버가 질문 벡터와 모델 식별자를 함께 보내고 Spring은 동일 모델의 저장 벡터만 비교한다.
- 의미 검색 후보가 최종 키워드 재조회에서 탈락하지 않도록 순위와 무관한 ID 기반 검증 경로를 사용한다.
- 공개 응답의 `retrievalMode` 변경은 임베딩 갱신·백필 완료 후 적용한다.

## 후속 작업

- 모임 생성·수정 시 임베딩 갱신
- 기존 모임 임베딩 백필
- 전체 데이터 준비 후 `retrievalMode=hybrid` 전환과 검색 품질 평가
