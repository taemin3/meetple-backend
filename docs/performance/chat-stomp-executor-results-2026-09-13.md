# STOMP executor 병렬도 실험 결과 — 2026-09-13

> 재현성 보충: 후속 clean bootRun에서 기존 `taskExecutor()` builder 설정이 actuator core 1로 남는 현상을 확인했다. `perf/chat-sequence-lock-scope`에서 명시적 `ThreadPoolTaskExecutor` 전달 방식과 런타임 설정 테스트를 추가했다. 아래 worker 4 결과 파일은 당시 runtime pool size 최대 4가 기록된 측정값이지만, 동일 설정 재현에는 후속 수정이 필요하다.

## 목적

기본 client inbound/outbound executor의 worker 1개가 100건/s 적체의 선행 병목인지 확인하고, worker를 늘렸을 때 같은 meeting 행의 `PESSIMISTIC_WRITE` 잠금 경합이 실제로 드러나는지 측정한다.

## 변경한 변수

- client inbound executor: core/max `1 → 4`, queue `무제한 → 1,000`
- client outbound executor: core/max `1 → 4`, queue `무제한 → 5,000`
- 클라이언트 10개, 방 10개, 방별 구독자 10명, 256바이트 메시지
- 100건/s × 30초 = 조건별 3,000건
- 실제 FCM과 push Kafka consumer 비활성
- 집중·분산은 각각 새 독립 fixture와 고유 runId 사용

Actuator에서 변경 후 inbound/outbound 모두 core/max 4, 부하 중 실제 pool size 최대 4를 확인했다.

## 결과

모든 회차에서 `scheduled=attempted=committed=received=3000`이었고 미전송, STOMP 오류, DB 미저장, 저장 후 미수신, 중복 수신, 구독자 누락과 순번 공백은 0건이다.

| worker | 조건 | E2E p95 | inbound queue p95 | outbound queue p95 | lock lookup p95 | transaction p95 | DB 저장률 |
|---:|---|---:|---:|---:|---:|---:|---:|
| 1 | 집중 | 99.78초 | 74.25초 | 75.03초 | 2.64ms | 22.71ms | 27.61건/s |
| 1 | 분산 | 102.58초 | 77.81초 | 77.98초 | 2.72ms | 23.36ms | 26.74건/s |
| 4 | 집중 | 21.08초 | 16.37초 | 0.07ms | 163.00ms | 185.14ms | 61.43건/s |
| 4 | 분산 | 17.09초 | 13.84초 | 0.26ms | 42.80ms | 107.33ms | 72.66건/s |

worker 4에서 worker 1 대비 E2E p95는 집중 78.9%, 분산 83.3% 감소했고 DB 저장률은 각각 2.22배, 2.72배 증가했다. outbound queue p95가 1ms 미만으로 줄어 worker 1의 outbound 직렬 처리가 실제 선행 병목이었음을 확인했다.

## PostgreSQL 잠금과 pool

| 조건 | 잠금 포함 표본/전체 | 차단 세션 관측 | 최대 query age | Hikari active/pending 최대 | inbound queued 최대 |
|---|---:|---:|---:|---:|---:|
| 집중 | 488 / 1,000 | 2,797 | 835ms | 10 / 4 | 1,000 |
| 분산 | 104 / 972 | 128 | 155ms | 10 / 4 | 1,000 |

집중 조건의 lock lookup p95는 분산보다 3.81배, transaction p95는 1.72배 높았다. 같은 시간대에 집중 조건은 전체 잠금 표본의 48.8%에서 차단 관계가 관측됐고 분산은 10.7%였다. 집중 E2E p95도 분산보다 3.99초 높고 DB 저장률은 15.5% 낮았다.

따라서 worker 1 환경에서는 애플리케이션 큐가 DB 진입을 직렬화해 meeting 행 잠금 경합을 가렸다. worker 4로 선행 병목을 완화하자 동일 방의 meeting 행 잠금이 집중 조건을 추가로 느리게 만드는 후속 병목으로 관측됐다. 다만 분산 조건도 17.09초의 inbound 적체와 Hikari pending이 발생하므로 meeting 행 잠금만으로 전체 지연을 설명할 수 없다.

## 원인과 판단

1. 기본 worker 1과 무제한 큐가 최초 100건/s 적체의 선행 병목이었다.
2. 동기 `AFTER_COMMIT` fan-out과 구독자별 Redis 토큰·DB 접근 권한 조회가 inbound 처리 시간을 늘린다.
3. worker를 4개로 늘리면 outbound queue는 해소되지만 DB 동시 요청이 증가해 Hikari가 포화된다.
4. 이때 동일 방은 `PESSIMISTIC_WRITE` 잠금 대기가 집중되어 분산보다 추가로 느려진다.
5. worker 4 설정만으로는 입력 100건/s를 지속 처리하지 못했고 inbound queue가 용량 1,000에 도달했으므로 운영 적용 준비가 끝난 상태가 아니다.

이번 결과는 executor 증설을 최종 최적화로 채택하는 근거가 아니라 다음 병목을 드러낸 실험이다. 다음 변경은 구독자별 권한 조회 축소, 커밋 후 fan-out의 전용 bounded executor 분리, Hikari와 맞춘 inbound 병렬도 순서로 각각 한 변수씩 검증한다. 같은 방의 순번 직렬화 요구가 남으므로 단순히 잠금 행을 옮기는 것만으로 동일 방 직렬화가 사라진다고 주장하지 않는다.

## 검증

- `gradlew.bat compileJava test --tests "com.meetple.backend.global.websocket.*" --tests "com.meetple.backend.domain.chat.realtime.*" --no-daemon`: 성공
- 2건/s × 10초 smoke: 20/20건 전송·커밋·10명 수신, E2E p95 47.01ms
- 100건/s × 30초 집중·분산: 각 3,000건 전송·커밋·10명 수신
- PostgreSQL 잠금 표본: `chat-executor4-focused-locks.jsonl`, `chat-executor4-distributed-locks.jsonl`
- AWS/staging 배포와 실제 FCM 발송: 수행하지 않음

## 포트폴리오 문장

> STOMP inbound/outbound worker 1개가 DB 동시성을 가리는 선행 병목임을 계측하고 worker 4개 대조 실험으로 분산 채팅 E2E p95를 102.58초에서 17.09초로 낮췄습니다. 이후 단일 방에서 PostgreSQL 차단 표본 488/1,000과 lock p95 163ms가 드러나는 것을 확인해, 애플리케이션 큐와 DB 행 잠금의 단계적 병목을 분리했습니다.
