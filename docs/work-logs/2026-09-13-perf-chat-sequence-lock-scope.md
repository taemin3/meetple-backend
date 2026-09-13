# 작업 로그: perf/chat-sequence-lock-scope

## 기본 정보

- 날짜: 2026-09-13
- 브랜치: `perf/chat-sequence-lock-scope`
- 작업자: Codex
- 관련 PR: 미생성

## 사용자 요청

- 동일 채팅방의 meeting 행 쓰기 잠금 경합을 줄이는 순번 전용 행 방식을 구현하고 로컬 부하로 검증한다.

## 작업 목표

- 순서, `clientMessageId` 재전송, 메시지·Outbox 원자성을 보존하면서 긴 meeting 행 쓰기 잠금을 제거한다.
- focused/distributed 100건/s 비교와 PostgreSQL 차단 표본을 재현 가능하게 남긴다.

## 작업 흐름

1. Git 상태와 채팅 트랜잭션·마이그레이션·테스트를 확인했다.
2. 순번 전용 엔티티/저장소/V18 마이그레이션과 신규 모임 초기화를 구현했다.
3. meeting 공유 잠금, 잠금 전 조회 이동, 잠금 후 중복 재확인을 적용했다.
4. STOMP executor 설정의 clean bootRun 재현 문제를 발견해 명시적 executor 전달과 런타임 테스트를 추가했다.
5. 전체 테스트, smoke, 조건별 독립 3회 100건/s 및 PostgreSQL 잠금 샘플링을 수행했다.

## 사용한 도구

- PowerShell, Gradle, Docker Compose, Testcontainers
- STOMP 전용 Node.js 부하 스크립트
- PostgreSQL `pg_stat_activity`, actuator metrics

## 실행한 주요 명령

```powershell
.\gradlew.bat test
.\gradlew.bat bootRun
.\performance\New-ChatLoadFixture.ps1 ...
.\performance\Capture-ChatPostgresLocks.ps1 ...
.\performance\Invoke-ChatContention.ps1 ... -Rps 100 -DurationSeconds 30
```

## 변경 파일 요약

- `ChatRoomSequence`, repository, V18 migration 추가
- `ChatService`의 meeting 쓰기 잠금을 공유 잠금과 순번 행 쓰기 잠금으로 분리
- `MeetingService`와 부하 fixture에 순번 행 생성 추가
- STOMP executor 설정을 명시적 bounded executor 방식으로 수정
- 단위·통합·마이그레이션·executor 설정 테스트 보강
- fixture 정리 스크립트 및 성능 결과 문서 추가

## 검증

```powershell
.\gradlew.bat test
```

결과:

- 전체 474개 테스트 성공
- PostgreSQL V18 신규 DB migration 및 `ddl-auto=validate` 성공
- focused/distributed smoke 각 20/20 저장·수신
- 독립 3회 조건별 각 3,000/3,000 저장·수신, 오류·중복·순번 공백 0
- 상세 수치는 `docs/performance/chat-sequence-lock-scope-results-2026-09-13.md` 참고

## 이슈와 결정

- PostgreSQL 행 잠금은 커밋까지 유지되므로 순번 전용 행만으로 동일 방 직렬화가 사라진다고 주장하지 않는다.
- 별도 순번 트랜잭션은 롤백 시 공백과 커밋 순서 역전 때문에 사용하지 않았다.
- 첫 부하 실행에서 stale worker 1 class를 발견해 결과에서 제외하고 clean build와 actuator 검증을 추가했다.
- 추가 역순 보충 부하는 Codex 실행 한도로 시작 전에 차단됐다.
- raw 결과, JFR, 기존 미추적 파일은 Git에 포함하지 않는다.

## 후속 작업

- 충분한 워밍업과 실행 순서 교차로 로컬 반복을 보충한다.
- 순번 잠금 이후 읽음/Outbox 경로를 한 변수씩 줄여 focused 차단을 재측정한다.
- 구독자별 outbound 인증 조회와 `AFTER_COMMIT` fan-out을 별도 실험으로 최적화한다.
