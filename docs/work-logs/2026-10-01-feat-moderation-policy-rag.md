# 작업 로그: feat/moderation-policy-rag

## 기본 정보

- 날짜: 2026-10-01
- 브랜치: `feat/moderation-policy-rag`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- 신고 분석의 초기 핵심 기능으로 운영 정책 RAG 저장·임베딩·검색 기반을 구현한다.
- 기존 pgvector와 AI 내부 인증 구조를 재사용하고 운영 데이터는 변경하지 않는다.

## 작업 목표

- 버전과 유효 기간을 갖는 정책, 조항 chunk, 모델별 임베딩 테이블을 추가한다.
- 현재 유효하고 신고 대상·정책 유형이 일치하는 정책만 하이브리드 검색한다.
- 정책 원문 해시가 바뀌면 임베딩 작업 대상으로 다시 조회되게 한다.
- AI 내부 API는 로그인 JWT가 아닌 별도 서비스 키를 사용한다.

## 작업 흐름

1. 기존 Flyway, pg_bigm, pgvector, AI 검색 내부 API와 인증 구조를 조사했다.
2. `V25` 마이그레이션과 개인정보가 없는 테스트 fixture를 추가했다.
3. 임베딩 작업 조회·stale 방지 upsert·정책 검색 API를 구현했다.
4. 실제 PostgreSQL과 전체 백엔드 테스트로 검증했다.

## 사용한 도구

- `rg`
- `apply_patch`
- Gradle
- Testcontainers PostgreSQL

## 실행한 주요 명령

```bash
./gradlew.bat test --tests com.meetple.backend.domain.moderation.policy.* --tests com.meetple.backend.global.database.FreshDatabaseMigrationTest
./gradlew.bat test
```

## 변경 파일 요약

- `V25__create_moderation_policy_rag.sql`: 정책·조항·임베딩 테이블과 검색 인덱스
- `domain/moderation/policy`: 내부 API 계약, 서비스 인증, 임베딩 작업, 하이브리드 검색
- `application.yml`, `.env.example`: 비활성 기본값의 AI 신고 분석 서비스 인증 설정
- `moderation-policies.sql`: 개인정보 없는 테스트 정책 fixture
- 정책 저장소·서비스·인증·컨트롤러·빈 DB 마이그레이션 테스트

## 검증

```bash
./gradlew.bat test
```

결과:

- 전체 528개 테스트 통과
- 실패 0, 오류 0, 제외 0
- 실제 PostgreSQL에서 Flyway 26개 마이그레이션, pg_bigm 인덱스, vector(1536), HNSW 인덱스 검증
- 같은 임베딩 모델 비교, 현재 유효 정책 필터, 대상·정책 유형 필터, keyword fallback, stale hash 거부 검증
- 시행 예정 정책은 검색에서 제외하지만 임베딩 사전 생성 대상으로 조회되는 경계 검증

## 이슈와 결정

- 운영 정책이나 운영 DB 데이터는 추가하지 않았다. 초기 데이터는 테스트 fixture에만 존재한다.
- 임베딩 모델별 복합 기본 키를 사용해 모델 전환 중에도 벡터를 구분한다.
- 정책 검색은 현재 유효한 정책만 포함하지만, 임베딩 작업은 시행 예정인 활성 정책도 미리 준비할 수 있다.
- AI가 제출할 정책 ID의 검색 후보 검증과 검색 이력 저장은 신고 분석 결과 모델 PR에서 추가한다.
- 정책 등록·수정 관리자 API와 React 화면은 관리자 단계로 남긴다.

## 후속 작업

- FastAPI 신고 분석 계약과 LangGraph 구현
- Kafka 신고 분석 이벤트 소비와 Spring 신고 문맥 API 연결
- 검색 후보 이력, AI 결과의 증거·정책 ID 검증 및 분석 결과 저장

## 리뷰 반영

- 필터 조건 때문에 HNSW 후보가 소진되는 문제를 막기 위해 검색 트랜잭션에
  `hnsw.iterative_scan=strict_order`와 `hnsw.max_scan_tuples=50000`을 적용했다.
- 벡터 후보 수를 요청 `limit`의 10배이자 최소 100개로 확장했다.
