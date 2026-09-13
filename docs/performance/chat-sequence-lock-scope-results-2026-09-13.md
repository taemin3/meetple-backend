# 채팅 순번 잠금 범위 축소 결과 — 2026-09-13

## 결론

`meetings` 행의 `PESSIMISTIC_WRITE`를 채팅 순번 전용 행으로 분리하고, 잠금 전 조회를 이동하자 100건/s 로컬 실험의 E2E p95 중앙값이 집중 조건 `21.08초 → 2.31초`, 분산 조건 `17.09초 → 0.40초`로 감소했다. 다만 집중 조건의 순번 잠금 p95 중앙값은 `11.02ms`로 분산 `1.23ms`보다 8.96배 높고 PostgreSQL 차단도 집중 조건에서만 관측됐다. 따라서 동일 방 직렬화는 제거된 것이 아니라 더 짧은 임계 구역으로 축소됐다.

## 구현

- `chat_room_sequences(meeting_id PK, last_sequence)` 추가 및 기존 메시지 최대 순번 backfill
- 신규 모임 생성 시 순번 행을 같은 트랜잭션에 생성
- 전송 시 meeting에는 공유 `PESSIMISTIC_READ`를 사용해 동시 채팅끼리는 막지 않으면서 모임 종료·취소 변경과의 순서를 보존
- 접근 검사, 회원 조회, 푸시 대상 조회를 순번 행 쓰기 잠금 전에 수행
- 순번 행 쓰기 잠금 후 `clientMessageId`를 다시 조회하고 순번 증가, 메시지·읽음 상태·Outbox를 한 트랜잭션으로 커밋
- `(meeting_id, room_sequence)` 및 `(meeting_id, sender_id, client_message_id)` 기존 유니크 제약 유지
- 부하 fixture 생성·정리 스크립트가 순번 행 및 datasetId 범위를 처리하도록 갱신

PostgreSQL 행 잠금은 SQL 문 종료가 아니라 트랜잭션 커밋까지 유지된다. 별도 `REQUIRES_NEW` 순번 발급은 잠금을 더 빨리 풀 수 있지만 메시지 저장 실패 시 순번 공백과 커밋 순서 역전이 생기므로 적용하지 않았다.

## 재현 조건

- 로컬 PostgreSQL 16, Redis, Spring Boot 단일 프로세스
- STOMP inbound/outbound core worker 4, queue 1,000/5,000
- Hikari maximum pool size 10
- 클라이언트 10개, 방 10개, 방별 구독자 10명, 메시지 256바이트
- 조건별 100건/s × 30초 = 3,000건, 독립 fixture 사용
- 연결·인증·구독 후 2초 워밍업, 최대 120초 정착
- 실제 FCM 및 push/email/image Kafka consumer 비활성
- 잠금 표본 0.1초 간격

첫 반복은 서버 재시작 직후 실행되어 cold/JIT 영향이 컸다. 평균으로 이를 숨기지 않고 개별값과 중앙값을 함께 보고한다. 추가 역순 보충 실험은 Codex 실행 한도 때문에 부하 시작 전에 차단됐다. `r4` fixture 행은 생성됐지만 부하 메시지는 생성되지 않았다.

## 독립 3회 결과

모든 실행에서 `scheduled=attempted=committed=received=3,000`, 미전송·STOMP 오류·DB 미저장·저장 후 미수신·중복 수신·순번 중복·순번 공백은 0건이었다.

| 회차 | 조건 | E2E p50 | E2E p95 | E2E p99 | 순번 잠금 p95 | 트랜잭션 p95 | inbound queue p95 | Hikari active/pending 최대 |
|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 1 cold | 집중 | 1.09초 | 10.53초 | 11.40초 | 117.63ms | 136.01ms | 9.30초 | 10 / 4 |
| 1 cold | 분산 | 0.79초 | 1.73초 | 1.76초 | 1.48ms | 34.25ms | 1.70초 | 4 / 0 |
| 2 warm | 집중 | 0.56초 | 1.85초 | 1.96초 | 10.61ms | 31.98ms | 1.82초 | 4 / 0 |
| 2 warm | 분산 | 71.54ms | 396.62ms | 521.89ms | 1.23ms | 25.11ms | 362.66ms | 4 / 0 |
| 3 warm | 집중 | 2.02초 | 2.31초 | 2.34초 | 11.02ms | 34.07ms | 2.27초 | 4 / 0 |
| 3 warm | 분산 | 29.75ms | 296.32ms | 393.50ms | 1.13ms | 22.76ms | 262.66ms | 4 / 0 |
| 중앙값 | 집중 | 1.09초 | 2.31초 | 2.34초 | 11.02ms | 34.07ms | 2.27초 | - |
| 중앙값 | 분산 | 71.54ms | 396.62ms | 521.89ms | 1.23ms | 25.11ms | 362.66ms | - |

## PostgreSQL 차단 표본

| 회차 | 조건 | 차단 포함 표본/전체 | 차단 세션 관측 | 최대 query age |
|---:|---|---:|---:|---:|
| 1 | 집중 | 263 / 590 | 910 | 395ms |
| 1 | 분산 | 0 / 543 | 0 | 0ms |
| 2 | 집중 | 193 / 541 | 198 | 67ms |
| 2 | 분산 | 0 / 531 | 0 | 0ms |
| 3 | 집중 | 201 / 549 | 204 | 43ms |
| 3 | 분산 | 0 / 523 | 0 | 0ms |

집중 조건 차단 쿼리의 대부분은 `chat_room_sequences ... for no key update`였다. 세 회차 합계로 집중은 657/1,680개 표본에서 1,312개 차단 세션이 관측됐고 분산은 0/1,597개였다. 즉 전용 행으로 옮긴 뒤에도 동일 방 순번 발급은 직렬화되지만, 분산 방끼리는 서로의 순번 행을 막지 않았다.

## 이전 worker 4 기준과 비교

| 지표 | 기존 집중 | 변경 집중 중앙값 | 기존 분산 | 변경 분산 중앙값 |
|---|---:|---:|---:|---:|
| E2E p95 | 21.08초 | 2.31초 | 17.09초 | 396.62ms |
| 잠금 조회 p95 | 163.00ms | 11.02ms | 42.80ms | 1.23ms |
| 트랜잭션 p95 | 185.14ms | 34.07ms | 107.33ms | 25.11ms |
| Hikari pending 최대 | 4 | warm 0 | 4 | 0 |

E2E p95는 집중 89.1%, 분산 97.7% 감소했다. 이 값은 로컬 단일 프로세스 결과이며 운영 성능으로 일반화하지 않는다. 집중 조건은 여전히 분산보다 E2E p95 5.82배, 순번 잠금 p95 8.96배 높아 다음 병목은 전용 순번 행의 커밋까지 유지되는 잠금과 inbound queue다.

## executor 재현성 수정

후속 clean bootRun 검증에서 기존 `registration.taskExecutor().corePoolSize(4)` 방식은 소스와 달리 actuator core 값이 1로 나타났다. 명시적으로 구성한 `ThreadPoolTaskExecutor`를 `registration.executor(...)`에 전달하도록 수정하고, 런타임 bean의 core/max/queue를 검증하는 테스트를 추가했다. 부하 전 actuator에서 inbound/outbound core 4를 확인했다.

## 재실행

```powershell
.\performance\New-ChatLoadFixture.ps1 `
  -DatasetId chat-seq-focused-r1 `
  -ManifestPath C:\secure\chat-seq-focused-r1.json `
  -AcknowledgeLocalDataCreation

.\performance\Capture-ChatPostgresLocks.ps1 `
  -OutputPath .\performance\results\chat-contention\chat-seq-focused-r1-locks.jsonl `
  -IntervalSeconds 0.1

.\performance\Invoke-ChatContention.ps1 `
  -Manifest C:\secure\chat-seq-focused-r1.json `
  -Scenario focused `
  -RunId chat-seq-focused-r1 `
  -Rps 100 `
  -DurationSeconds 30 `
  -WarmupSeconds 10 `
  -SettleSeconds 120
```

분산 조건은 새 datasetId/manifest를 만들고 `-Scenario distributed`로 실행한다. raw JSON/JSONL은 `performance/results/`에 남지만 Git에는 포함하지 않는다.

정리 시 먼저 runId 메시지를 지우고 fixture를 제거한다.

```powershell
.\performance\Remove-ChatLoadRun.ps1 `
  -RunId chat-seq-focused-r1 `
  -AcknowledgeSyntheticDataDeletion

.\performance\Remove-ChatLoadFixture.ps1 `
  -DatasetId chat-seq-focused-r1 `
  -AcknowledgeLocalDataDeletion
```

## 포트폴리오 문장

> STOMP 계측과 PostgreSQL 차단 관계를 대조해 동일 채팅방의 모임 행 쓰기 잠금을 병목으로 확인하고, 방별 순번 전용 행과 잠금 전 조회 분리로 순서·재전송·Outbox 원자성을 유지했습니다. 로컬 100건/s 독립 3회 비교에서 집중/분산 E2E p95 중앙값을 각각 21.08초→2.31초, 17.09초→0.40초로 줄였으며, 남은 동일 방 순번 직렬화도 잠금 p95 11.02ms와 차단 표본으로 분리했습니다.
