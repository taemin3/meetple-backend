# 작업 로그: perf/chat-inbound-worker-breakdown

## 기본 정보

- 날짜: 2026-09-13
- 브랜치: `perf/chat-inbound-worker-breakdown`
- 작업자: Codex
- 관련 PR: 미생성

## 사용자 요청

- 동일 방 집중 시 `inboundQueue` p95가 증가하는 원인을 실제 계측과 한 변수 실험으로 확인한다.

## 작업 목표

- 순번 잠금 획득 후 커밋까지의 시간을 실제 커밋 경계에서 측정한다.
- 동일 총 전송량에서 방 분산과 구독자 수 변경을 비교해 DB 잠금과 동기 fan-out 영향을 분리한다.

## 작업 흐름

1. 기존 transaction, realtime 계측 경계를 다시 확인했다.
2. `lockHeldUntilCommit` 지표와 잠금 샘플러 quiet 모드를 추가했다.
3. 로컬 100 RPS focused/distributed와 distributed 1 subscriber를 실행했다.
4. SQL 콘솔 출력으로 오염된 최초 진단 2회는 제외하고 로그를 끈 새 fixture 결과만 채택했다.
5. 메시지 ID, DB 커밋 ID, 수신 ID와 순번 정합성을 검증했다.

## 사용한 도구

- PowerShell, Gradle, Docker Compose
- STOMP 전용 Node.js 부하 스크립트
- PostgreSQL `pg_stat_activity`, actuator metrics

## 실행한 주요 명령

```powershell
.\gradlew.bat test --tests "..."
.\performance\Capture-ChatPostgresLocks.ps1 ... -Quiet
.\performance\Invoke-ChatContention.ps1 ... -Rps 100 -DurationSeconds 30
```

## 변경 파일 요약

- 잠금 획득 직후부터 실제 `afterCommit`까지의 계측 추가
- 잠금 JSONL을 콘솔 출력 없이 기록하는 `-Quiet` 옵션 추가
- 부하 실행 요약에 `lockHeldUntilCommitP95Us` 추가
- inbound queue 원인 분해 결과 문서 추가

## 검증

- 전체 474개 테스트 성공
- 계측 단위 테스트 성공
- 유효한 세 조건 모두 3,000/3,000 커밋·수신
- 미전송·미저장·저장 후 미수신·오류·중복·순번 공백 0
- 집중에서만 PostgreSQL 차단 1,049건 관측
- 분산 구독자 10→1 변경 시 inbound queue p95 `1.37초→0.118ms`

## 이슈와 결정

- local SQL 전체 출력이 PTY 역압을 만들어 집중/분산 모두 약 23초가 된 최초 2회는 비교에서 제외했다.
- `ExecutorChannelInterceptor.afterMessageHandled`는 기대한 서비스 완료 경계와 일치하지 않아 handler 점유 계측으로 사용하지 않고 제거했다.
- repository 호출 시간 전체를 잠금 대기로 해석하지 않았다.
- raw 결과와 기존 미추적 파일은 Git에 포함하지 않는다.

## 후속 작업

- 방별 순서를 보존하는 비동기 fan-out 설계를 별도 변경으로 검증한다.
- 구독자 10/50/100명과 권한 철회 반영 시간까지 측정한다.
