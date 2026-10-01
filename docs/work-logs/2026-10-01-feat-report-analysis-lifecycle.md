# 작업 로그: feat/report-analysis-lifecycle

## 기본 정보

- 날짜: 2026-10-01
- 브랜치: `feat/report-analysis-lifecycle`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- AI 신고 분석 기능의 다음 단계 작업을 진행하고 브랜치에 푸시한다.

## 작업 목표

- 신고 당시 콘텐츠를 기존 신고에 변경 불가능한 스냅샷으로 저장한다.
- AI 분석 결과의 신고 스냅샷·적용 정책 참조를 서버에서 검증하고 멱등 저장한다.
- Retry 가능 실패와 영구 실패 상태를 구분한다.

## 작업 흐름

1. V26 마이그레이션으로 기존 신고의 스냅샷 컬럼과 분석 상태·결과, 적용 정책 테이블을 추가했다.
2. 신고 생성 트랜잭션에서 대상 콘텐츠 스냅샷과 `PENDING` 분석을 저장한 후 Outbox 이벤트를 발행하도록 연결했다.
3. AI 서비스용 문맥 조회와 분석 완료·실패 API를 추가했다.
4. 증거와 정책 ID 소속, 위험도와 권고 조치 조합, 결과 멱등성 및 상태 전이를 검증했다.
5. 단위·웹·PostgreSQL 저장소·빈 DB 마이그레이션·전체 회귀 테스트를 실행했다.

## 사용한 도구

- `exec_command`
- `apply_patch`
- Gradle Test
- Testcontainers PostgreSQL

## 실행한 주요 명령

```powershell
.\gradlew.bat compileJava
.\gradlew.bat test --tests "com.meetple.backend.domain.moderation.analysis.*" --tests "com.meetple.backend.domain.moderation.service.ModerationServiceTest" --tests "com.meetple.backend.global.database.FreshDatabaseMigrationTest"
.\gradlew.bat test
git diff --check
```

## 변경 파일 요약

- `V26__create_report_analysis_lifecycle.sql`: 기존 신고 스냅샷, 분석 수명주기, 적용 정책 스키마
- `ReportAnalysisContracts`: 내부 AI API 계약과 상태·판단 enum
- `ReportAnalysisRepository`: 신고 스냅샷, 결과·실패 상태, 적용 정책 JDBC 저장
- `ReportAnalysisService`: 참조 검증, 조치 검증, 결과 해시·멱등성
- `ReportAnalysisToolController`: AI 서비스 토큰 기반 내부 API
- `ModerationService`: 신고 대상 콘텐츠 스냅샷 초기화 연결
- 테스트: 서비스, 웹 보안, PostgreSQL 저장, 빈 DB 마이그레이션, 기존 신고 생성 회귀

## 검증

```text
집중 테스트: BUILD SUCCESSFUL
전체 테스트: 544 tests, failures 0, skipped 0
git diff --check: 이상 없음
```

## 이슈와 결정

- 적용된 V25는 수정하지 않고 V26을 새로 추가했다.
- 신규 테이블은 `report_analyses`, `report_analysis_policies` 두 개만 추가했다.
- 정책 검색은 기존 `/internal/ai/moderation/policies/search`를 재사용하고 검색 후보 이력은 저장하지 않는다.
- 증거는 별도 테이블로 분리하지 않고 기존 `reports`에 신고 시점 스냅샷으로 저장한다.
- 회원 증거에는 이메일·닉네임·지역을 포함하지 않고 공개 소개만 저장한다.
- 실패 콜백에는 원문 오류를 받지 않고 제한된 `failureCode`만 저장한다.
- 완료 결과의 정책은 서울 기준 현재 유효 기간과 신고 대상 유형을 다시 검증한다.
- `confidence`는 DB 정밀도와 동일하게 소수 4자리까지 허용한다.
- 실패 콜백의 `retryable`은 누락 시 영구 실패로 오인되지 않도록 필수값으로 검증한다.
- `FAILED_PERMANENT`와 `COMPLETED` 상태는 늦게 도착한 콜백으로 되돌릴 수 없게 했다.

## 후속 작업

- AI 저장소에 Kafka 기본·Retry·DLQ 소비자와 백엔드 문맥/검색/결과 콜백 연동을 추가한다.
- 관리자 제재 기능은 분석 결과 수집 경로가 완성된 뒤 별도 PR로 진행한다.
