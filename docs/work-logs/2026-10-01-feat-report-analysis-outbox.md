# 작업 로그: feat/report-analysis-outbox

## 기본 정보

- 날짜: 2026-10-01
- 브랜치: `feat/report-analysis-outbox`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- 사용자 신고를 저장한 뒤 AI 분석을 비동기로 시작할 수 있는 가장 작은 기반을 구현한다.
- 기존 Outbox, Debezium, Kafka 구조를 재사용하고 민감한 신고 원문은 이벤트에 넣지 않는다.

## 작업 목표

- 신고 저장 트랜잭션 안에서 `REPORT_ANALYSIS_REQUESTED` Outbox 이벤트를 발행한다.
- 이벤트에는 후속 Spring 내부 API 조회에 필요한 `reportId`만 포함한다.
- 로컬 Kafka에 신고 분석 기본, Retry, DLQ 토픽을 준비한다.

## 작업 흐름

1. 신고·차단, Outbox, Debezium, Kafka Retry/DLQ, pgvector, AI LangGraph 구조를 조사했다.
2. 신고 저장 직후 같은 트랜잭션에서 버전이 있는 분석 요청 이벤트를 발행했다.
3. 단위 테스트로 라우팅 메타데이터와 최소 payload를 검증했다.

## 사용한 도구

- `rg`
- `git`
- `apply_patch`
- Gradle

## 실행한 주요 명령

```bash
./gradlew test
```

## 변경 파일 요약

- `ModerationService`: 신고 저장 후 분석 요청 Outbox 발행
- `ReportAnalysisRequestedEvent`: `reportId` 전용 이벤트 데이터 계약
- `OutboxEventTopic`: 신고 분석 토픽 추가
- `docker-compose.yml`: 로컬 기본, Retry, DLQ 토픽 추가
- `infra/terraform/event_runtime.tf`: 운영 기본, Retry, DLQ 토픽과 보존 정책 추가
- `ModerationServiceTest`: 이벤트 계약 검증

## 검증

```bash
./gradlew test
```

결과:

- 전체 513개 테스트 통과, 실패 0, 오류 0, 제외 0
- 변경 관련 `ModerationServiceTest`, `OutboxEventPublisherIntegrationTest` 선택 실행 통과
- `docker-compose config` 구성 검증 통과. 사용자 Docker 설정 파일 접근 경고는 있었으나 구성 오류는 없었다.
- 번들 Terraform 1.16.0으로 `terraform fmt -check -recursive` 통과
- 번들 Terraform 1.16.0으로 `terraform validate -no-color` 통과. 기존 Service Discovery `failure_threshold` deprecation 경고만 확인

## 이슈와 결정

- 신고 설명, 사용자 JWT, 회원 개인정보는 Kafka payload에 포함하지 않았다.
- AI 서버는 후속 PR에서 서비스 인증이 적용된 Spring 내부 API로 신고 문맥을 조회한다.
- AI 소비, 정책 RAG, 분석 결과 저장, 자동 경고, 관리자 제재는 별도 PR로 분리한다.
- Kafka auto-create가 비활성화된 운영 환경도 같은 토픽을 명시적으로 프로비저닝한다.

## 후속 작업

- 운영 정책 및 신고 분석 데이터 모델과 Flyway 마이그레이션
- 정책 임베딩 생성과 하이브리드 검색 내부 API
- FastAPI Kafka 소비와 LangGraph 신고 분석
- Spring 결과 검증, 자동 경고, 관리자 승인 제재
