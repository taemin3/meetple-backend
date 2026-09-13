# 채팅 inbound queue 원인 분해 — 2026-09-13

## 결론

`inboundQueue`는 독립적인 처리 단계가 아니라 네 개 inbound worker가 앞선 메시지를 동기 처리하는 동안 생긴 대기다. 동일 방 집중에서는 순번 행 잠금 대기가 worker를 점유했고, 모든 조건에서는 커밋 후 로컬 fan-out과 구독자별 권한 확인이 같은 inbound 호출 흐름을 계속 점유했다.

분산 조건에서 다른 조건은 유지하고 실시간 구독자만 10명에서 1명으로 줄이자 E2E p95는 `1.40초 → 31.58ms`, inbound queue p95는 `1.37초 → 0.118ms`, local fan-out p95는 `29.19ms → 1.63ms`로 줄었다. `outboundAuth` 호출 수도 `30,000 → 3,000`으로 감소했다. 따라서 분산 조건에 남은 queue 적체의 주된 공통 비용은 동기 fan-out과 구독자별 Redis 토큰·DB 채팅방 접근 확인이다.

## 추가 계측

- `lockHeldUntilCommit`: 순번 행 잠금 조회가 반환된 직후부터 실제 트랜잭션 `afterCommit`까지 측정
- PostgreSQL 잠금 샘플러 `-Quiet`: JSONL 파일은 유지하고 터미널 출력을 억제해 측정 프로세스의 출력 역압을 방지

`lockLookup`에는 커넥션 획득과 SQL 실행이 포함되므로 PostgreSQL `wait_event_type = 'Lock'` 및 `pg_blocking_pids()` 표본과 함께 해석했다.

## 조건

- 로컬 Spring Boot 단일 프로세스, PostgreSQL 16, Redis
- STOMP inbound/outbound worker 4, Hikari max 10
- 10개 송신 클라이언트, 방 10개, 100건/s × 30초 = 3,000건
- 메시지 256바이트, 연결·인증·구독 및 워밍업 완료 후 측정
- 실제 FCM 및 push/email/image Kafka consumer 비활성
- SQL 전체 출력과 Hibernate SQL debug 로그 비활성
- focused/distributed는 실시간 구독자 10명, 추가 대조는 distributed 구독자 1명

모든 유효 실행에서 scheduled·attempted·committed·received는 각각 3,000건이고 미전송·미저장·저장 후 미수신·STOMP 오류·중복 수신·순번 중복·순번 공백은 0건이었다.

## 결과

| 조건 | E2E p95 | inbound queue p95 | 잠금 조회 p95 | 잠금 획득→커밋 p95 | 트랜잭션 p95 | local fan-out p95 | outbound auth |
|---|---:|---:|---:|---:|---:|---:|---:|
| 집중, 구독자 10 | 11.60초 | 9.97초 | 145.87ms | 19.55ms | 164.79ms | 30.07ms | 30,000회, p95 2.95ms |
| 분산, 구독자 10 | 1.40초 | 1.37초 | 1.42ms | 15.94ms | 26.88ms | 29.19ms | 30,000회, p95 2.87ms |
| 분산, 구독자 1 | 31.58ms | 0.118ms | 1.13ms | 13.00ms | 21.48ms | 1.63ms | 3,000회, p95 1.45ms |

런타임 표본에서 집중 조건은 inbound worker 평균 4/4, inbound queue 최대 1,000, Hikari active/pending 최대 10/4였다. 분산 10명은 inbound worker 평균 3.48/4, queue 최대 377, Hikari 최대 4/0이었고, 분산 1명은 inbound worker 평균 2.11/4, queue 최대 17, Hikari 최대 4/0이었다.

## PostgreSQL 차단 관계

| 조건 | 차단 포함 표본/전체 | 차단 세션 관측 | 최대 query age |
|---|---:|---:|---:|
| 집중, 구독자 10 | 276/1,689 | 1,049 | 1,213ms |
| 분산, 구독자 10 | 0/1,671 | 0 | 0ms |

집중 조건 차단 쿼리는 대부분 `chat_room_sequences ... for no key update`였다. 잠금 획득 후 커밋까지는 p95 19.55ms였지만 그 한 행을 기다리는 요청과 Hikari 대기가 겹치며 `lockLookup` p95가 145.87ms까지 증가했다. repository 호출 전체를 전부 잠금 대기로 간주하지는 않는다.

## 해석

1. 집중과 분산의 차이는 동일 순번 행 경합이다. focused에서만 PostgreSQL 차단과 Hikari pending이 관측됐다.
2. 분산 10명과 분산 1명의 차이는 구독자별 동기 fan-out이다. 메시지 저장·읽음 상태·Outbox 경로는 동일하고 실시간 구독자 수만 변경했다.
3. 현재 `AFTER_COMMIT` listener는 inbound 호출 스레드에서 로컬 fan-out과 Redis publish를 동기 실행한다. 구독자마다 토큰 상태 Redis 조회와 meeting/참여 권한 DB 조회도 반복한다.
4. worker 수만 늘리면 순간 queue는 완화할 수 있지만 동일 순번 행 대기와 DB 연결 사용량을 함께 늘리므로 근본 해결로 단정할 수 없다.

이번 수치는 원인 분해용 단일 추가 회차다. 절대 성능 수치 확정에는 실행 순서를 교차한 3회 반복이 더 필요하지만, 기존 독립 3회 focused/distributed 결과와 이번 구독자 한 변수 결과가 같은 방향을 보였다.

## 다음 변경 후보

- 커밋 후 fan-out을 inbound worker에서 분리하되 방별 `roomSequence` 처리 순서를 보존하는 bounded executor 사용
- CONNECT/SUBSCRIBE에서 토큰과 접근 권한을 검증하고 로컬 session registry에 승인된 room을 유지
- 로그아웃·참여 취소·강퇴·모임 취소 시 해당 세션을 즉시 무효화하고, 짧은 TTL 재검증으로 누락을 보완
- 변경 후 구독자 10/50/100명에서 E2E, inbound/outbound queue, DB QPS, 권한 철회 반영 시간을 함께 측정

비동기 fan-out 변경은 수신 순서, 권한 철회 지연, executor 포화 시 거부 정책을 먼저 정의해야 하므로 이번 브랜치에서는 적용하지 않았다.

## 재실행

서버 실행 시 local 프로필의 SQL 출력을 꺼야 한다.

```powershell
$env:MEETPLE_PERFORMANCE_CHAT_SEND_ENABLED='true'
$env:PUSH_KAFKA_CONSUMER_ENABLED='false'
$env:PUSH_FCM_ENABLED='false'
$env:EMAIL_DELIVERY_KAFKA_CONSUMER_ENABLED='false'
$env:IMAGE_DELETION_KAFKA_CONSUMER_ENABLED='false'
$env:SPRING_JPA_SHOW_SQL='false'
$env:SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL='false'
$env:LOGGING_LEVEL_ORG_HIBERNATE_SQL='OFF'
```

```powershell
.\performance\Capture-ChatPostgresLocks.ps1 `
  -OutputPath .\performance\results\chat-contention\<runId>-locks.jsonl `
  -IntervalSeconds 0.1 `
  -Quiet

.\performance\Invoke-ChatContention.ps1 `
  -Manifest C:\secure\<manifest>.json `
  -Scenario distributed `
  -RunId <runId> `
  -Rps 100 `
  -DurationSeconds 30 `
  -WarmupSeconds 10 `
  -SettleSeconds 120 `
  -SubscribersPerRoom 1
```

raw JSON/JSONL과 manifest는 Git에 포함하지 않는다.
