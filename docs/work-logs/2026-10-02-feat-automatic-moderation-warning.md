# 작업 로그: feat/automatic-moderation-warning

## 기본 정보

- 날짜: 2026-10-02
- 브랜치: `feat/automatic-moderation-warning`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- AI 신고 분석 결과가 안전한 조건을 통과한 경우에만 Spring에서 자동 경고를 발송한다.
- 회원 정지와 모임 삭제 같은 제재는 자동 실행하지 않고 관리자 승인 범위로 남긴다.

## 작업 목표

- 기본 비활성화된 자동 경고 정책을 설정으로 관리한다.
- 검증된 신고 분석 중 보수적인 조건을 통과한 `WARNING` 결과만 처리한다.
- 같은 신고의 경고와 알림이 중복 생성되지 않게 한다.

## 작업 흐름

1. AI 분석 결과의 증거·정책 검증과 저장이 끝난 뒤 자동 경고 조건을 평가한다.
2. `report_warnings.report_id` 기본 키와 원자적 insert로 중복 경고를 차단한다.
3. 신고 대상 작성자를 찾아 고정된 경고 문구의 인앱 알림과 Push Outbox 이벤트를 생성한다.

## 사용한 도구

- `exec_command`
- `apply_patch`
- Gradle
- Testcontainers PostgreSQL

## 실행한 주요 명령

```powershell
.\gradlew.bat test --tests "com.meetple.backend.domain.moderation.analysis.ReportAnalysisServiceTest" --tests "com.meetple.backend.domain.moderation.warning.AutomaticWarningServiceTest" --tests "com.meetple.backend.domain.moderation.analysis.ReportAnalysisRepositoryTest" --tests "com.meetple.backend.global.database.FreshDatabaseMigrationTest"
.\gradlew.bat test
```

## 변경 파일 요약

- `V27__create_report_warnings.sql`: 신고별 자동 경고 이력과 중복 방지 제약 추가
- `AutomaticWarningProperties`: 활성화 여부, 확신도 기준, 허용 신고 유형 설정
- `AutomaticWarningRepository`: 회원·모임·채팅 신고의 대상 회원 조회와 원자적 경고 저장
- `AutomaticWarningService`: 보수적인 자동 경고 조건 평가와 고정 문구 알림 발송
- `ReportAnalysisService`: 검증된 분석 결과 저장 후 자동 경고 평가 연결
- 설정 예시와 서비스·저장소·마이그레이션 테스트 보강

## 검증

```powershell
.\gradlew.bat test
```

결과:

- 전체 549개 테스트 통과
- PostgreSQL/Testcontainers 기반 Flyway V27, 대상 회원 조회, 중복 경고 방지 검증 통과
- 외부 Push 발송은 비활성화된 테스트 설정을 사용했으며 실제 기기 수신은 검증하지 않음

## 이슈와 결정

- 최초 전체 테스트에서 새 Flyway 마이그레이션 개수 기대값 한 곳이 27로 남아 실패했고 28로 갱신했다.
- 자동 경고는 기본 비활성화했다.
- 기본 정책은 `SPAM`, `LOW` 위험도, `LOW` 또는 `NORMAL` 우선순위, `WARNING` 권고, 확신도 0.95 이상이다.
- LLM 요약이나 신고 원문을 알림에 사용하지 않고 고정 문구만 발송한다.
- 탈퇴 회원과 중복 신고 경고에는 알림을 생성하지 않는다.

## 후속 작업

- 관리자 신고 조회와 제재 승인 API
- 운영 환경 활성화 전 Kafka→AI→Spring→경고 Outbox 통합 검증
- 실제 기기 Push 수신과 앱 알림 표시 검증
