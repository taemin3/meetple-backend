# 채팅방 잠금 경합 로컬 측정 결과 — 2026-09-12

## 결론

측정한 로컬 환경에서는 같은 `meetings` 행의 `PESSIMISTIC_WRITE` 잠금이 채팅 전송의 실제 주 병목이라는 증거가 나오지 않았다.

- 20건/s에서는 집중·분산 모두 목표 전송률을 유지했고 실제 PostgreSQL 차단 표본은 0건이었다.
- 집중 조건의 E2E 꼬리 지연이 분산보다 반복적으로 높지 않았고 회차 간 변동이 더 컸다.
- 100건/s에서는 두 조건 모두 1분 30초가 넘는 수신 적체가 발생했지만 잠금 조회와 트랜잭션 시간은 밀리초 수준이었고 PostgreSQL 차단은 0건이었다.
- 100건/s의 `scheduled=attempted=3000`은 100건/s 입력에 성공했다는 뜻이며, 서버가 100건/s로 처리했다는 뜻이 아니다.
- 후속 계측에서는 집중·분산 모두 STOMP inbound/outbound executor queue에서 수십 초 대기가 확인됐다. 잠금 구조 변경은 보류하고 STOMP 처리 용량과 구독자별 fan-out 증폭을 우선 분석한다.

후속 계측은 이 후보를 분리하도록 구현했다. 결과의 `serverRealtime.phaseMicros`는 `inboundAuth`, `inboundQueue`, `outboundAuth`, `outboundQueue`, `localFanOut`, `redisPublish`의 표본 수와 p50/p95/p99/max를 제공한다. 기존 20건/s 3회 및 최초 100건/s 결과에는 이 값이 없고, 2026-09-13의 독립 fixture 비교에서 추가로 측정했다.

## 통제 조건

- 로컬 Spring Boot, PostgreSQL, Redis
- 클라이언트 10개, 방 10개, 모든 클라이언트가 모든 방 구독
- 방별 회원 10명, 푸시 대상 9명, 실시간 구독자 10명
- 실제 FCM과 Kafka consumer 비활성, 메시지·읽음 상태·Outbox 저장 경로 유지
- 각 측정 회차마다 메시지·읽음 상태·Outbox가 0인 독립 fixture 사용
- 집중은 첫 방에 전체 메시지 전송, 분산은 10개 방에 round-robin 전송
- 256바이트 메시지, 절대 시각 기반 전송 일정
- PostgreSQL `wait_event_type='Lock'` 또는 `pg_blocking_pids()`가 있는 세션을 0.1초 간격으로 표본화

초기에 같은 fixture를 재사용한 6회 실행은 초기 메시지 수가 달라 최종 비교에서 제외했다. 아래 결과는 `chatcmp-20260912-*` 독립 fixture 실행만 사용한다.

## 20건/s × 60초, 조건별 3회

모든 회차에서 `scheduled=attempted=committed=received=1200`이었다. 미전송, STOMP 오류, DB 미저장, 저장 후 미수신, 커밋 ID 불일치, 10명 관측 누락, 중복 수신, 방별 순번 중복·공백은 모두 0건이다.

| 조건 | 회차 | E2E p50/p95/p99 (ms) | lock lookup p95 (ms) | transaction p95 (ms) | schedule lag p95 (ms) | Hikari active/pending max | CPU avg/max | DB 차단/표본 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 집중 | 1 | 37.60 / 147.21 / 462.02 | 2.87 | 25.50 | 13.84 | 1 / 0 | 3.73% / 6.15% | 0 / 835 |
| 분산 | 1 | 36.37 / 117.44 / 319.57 | 3.00 | 23.24 | 13.90 | 1 / 0 | 3.68% / 5.98% | 0 / 830 |
| 집중 | 2 | 37.44 / 92.66 / 157.63 | 2.83 | 23.12 | 13.84 | 1 / 0 | 3.80% / 7.35% | 0 / 841 |
| 분산 | 2 | 37.16 / 54.50 / 111.94 | 2.78 | 23.20 | 14.06 | 1 / 0 | 3.81% / 6.25% | 0 / 832 |
| 집중 | 3 | 37.26 / 109.95 / 434.27 | 3.11 | 23.89 | 14.08 | 1 / 0 | 3.92% / 6.35% | 0 / 835 |
| 분산 | 3 | 36.75 / 244.87 / 436.72 | 3.02 | 23.75 | 13.77 | 1 / 0 | 3.75% / 6.92% | 0 / 832 |

회차별 통계의 중앙값:

| 조건 | E2E p50/p95/p99 중앙값 (ms) | lock p95 중앙값 (ms) | transaction p95 중앙값 (ms) | DB 차단/표본 |
|---|---:|---:|---:|---:|
| 집중 | 37.44 / 109.95 / 434.27 | 2.87 | 23.89 | 0 / 2,511 |
| 분산 | 36.75 / 117.44 / 319.57 | 3.00 | 23.24 | 0 / 2,494 |

분산 3회차 p95가 집중보다 높아, 집중 조건만 반복적으로 느려지는 패턴은 아니다. repository 호출 시간에는 커넥션 획득과 SQL 실행이 포함되지만, Hikari pending과 PostgreSQL 차단도 모두 0이어서 관측된 E2E 변동을 행 잠금 대기로 해석하지 않는다.

## 100건/s × 30초, 조건별 1회

전송 종료 후 최대 120초 동안 연결을 유지해 DB 커밋과 10명 수신을 대조했다. 두 조건 모두 최종 `scheduled=attempted=committed=received=3000`이며 오류와 정합성 문제는 0건이다.

| 조건 | E2E p50/p95/p99 (ms) | E2E max (ms) | lock p50/p95/p99 (ms) | transaction p50/p95/p99 (ms) | Hikari active/pending max | CPU avg/max | DB 차단/표본 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 집중 | 94,282.68 / 105,735.99 / 106,767.84 | 107,032.71 | 1.77 / 2.66 / 4.72 | 18.42 / 26.47 / 36.37 | 1 / 0 | 4.67% / 11.19% | 0 / 1,766 |
| 분산 | 84,348.40 / 95,716.33 / 96,707.20 | 96,956.85 | 1.71 / 2.41 / 4.03 | 15.85 / 21.13 / 29.34 | 1 / 0 | 4.68% / 10.38% | 0 / 1,611 |

집중 p95가 분산보다 약 10.02초 높지만 lock lookup p95 차이는 0.25ms, transaction p95 차이는 5.34ms이다. PostgreSQL 차단도 양쪽 모두 0건이므로 10초 차이를 모임 행 잠금으로 설명할 근거가 없다. 두 조건 모두 수십 초 적체가 발생한 공통 경로를 먼저 분리해야 한다.

120초 대기를 넣기 전 최초 집중 실행은 측정 종료 시 DB 1,308건, 수신 335건이었지만 이후 해당 run의 DB 메시지와 Outbox가 모두 3,000건으로 증가했다. 이는 전송 유실이 아니라 측정 종료 뒤에도 서버 큐가 계속 배출된 증거다. 해당 실행은 최종 비교 수치에서 제외한다.

## 100건/s STOMP 단계별 후속 계측 — 2026-09-13

새 독립 fixture와 고유 runId로 집중·분산을 각각 한 번 실행했다. 두 조건 모두 `scheduled=attempted=committed=received=3000`이었고 미전송, STOMP 오류, DB 미저장, 저장 후 미수신, 중복 수신과 관측자 누락은 0건이다. 전송 일정 지연 p95도 집중 13.12ms, 분산 13.31ms로 입력량이 응답 지연에 따라 줄어들지 않았다.

| 조건 | E2E p50/p95/p99 (ms) | inbound queue p50/p95/p99 (ms) | outbound queue p50/p95/p99 (ms) | lock lookup p95 (ms) | transaction p95 (ms) | local fan-out p95 (ms) | Redis publish p95 (ms) | outbound auth 표본 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 집중 | 88,542.37 / 99,779.61 / 100,793.75 | 39,873.69 / 74,252.92 / 77,376.24 | 41,657.36 / 75,032.47 / 77,879.79 | 2.64 | 22.71 | 23.66 | 1.05 | 30,000 |
| 분산 | 91,244.83 / 102,582.15 / 103,593.12 | 40,259.76 / 77,809.71 / 81,226.92 | 41,792.71 / 77,976.31 / 81,316.87 | 2.72 | 23.36 | 25.28 | 1.12 | 30,000 |

분산 조건의 E2E p95가 집중보다 2.80초 높고 inbound/outbound queue p95도 각각 3.56초, 2.94초 높았다. 반면 잠금 조회 p95 차이는 0.08ms, 트랜잭션 p95 차이는 0.65ms에 불과했다. 따라서 동일 방에 집중될 때만 악화되는 패턴이 아니며, 100건/s 적체의 직접 관측 지점은 DB 잠금 구간이 아니라 STOMP executor queue다.

3,000개 입력이 10개 구독자에게 전달되며 양쪽 모두 outbound 인증 30,000회가 수행됐다. 구독자별 인증·접근 확인과 로컬 fan-out이 STOMP 처리량을 증폭시키는 유력 원인이지만, 이번 결과만으로 개별 작업 중 하나를 유일한 근본 원인으로 확정하지 않는다. inbound와 outbound queue의 백분위는 서로 다른 표본 집합에서 계산되므로 두 값을 더해 E2E 지연으로 해석하지 않는다.

이번 두 후속 실행과 같은 이름의 PostgreSQL 잠금 표본 파일은 남아 있지 않다. 잠금 판단은 앞선 100건/s 비교의 집중 0/1,766, 분산 0/1,611 차단 표본과 이번 실행의 짧은 lock lookup, Hikari active 최대 1·pending 0을 함께 사용한다. 따라서 후속 실행과 정확히 같은 시간대의 차단 관계는 미측정이라는 한계를 유지한다.

## 원시 결과

20건/s 결과와 잠금 표본:

- `performance/results/chat-contention/chatcmp-20260912-focused-r1.json`
- `performance/results/chat-contention/chatcmp-20260912-distributed-r1.json`
- r2, r3도 같은 이름 규칙
- 각 결과의 `-locks.jsonl`

100건/s 결과와 잠금 표본:

- `performance/results/chat-contention/chatcmp-100-focused-r2.json`
- `performance/results/chat-contention/chatcmp-100-focused-r2-locks.jsonl`
- `performance/results/chat-contention/chatcmp-100-distributed-r2.json`
- `performance/results/chat-contention/chatcmp-100-distributed-r2-locks.jsonl`

STOMP 단계별 후속 결과:

- `performance/results/chat-contention/chat-stomp-focused-r2.json`
- `performance/results/chat-contention/chat-stomp-distributed-r2.json`

manifest는 비밀번호를 포함해 `C:\secure`에 두고 Git에 포함하지 않는다. 원시 결과는 합성 `runId`, 메시지·커밋 ID와 단계별 표본을 포함한다.

## 재실행

서버의 채팅 계측, 로컬 profile, FCM/Kafka consumer 비활성 설정을 확인한 뒤 각 조건에 새로운 독립 fixture와 새로운 runId를 사용한다.

```powershell
.\performance\Capture-ChatPostgresLocks.ps1 `
  -OutputPath .\performance\results\chat-contention\<runId>-locks.jsonl `
  -IntervalSeconds 0.1
```

```powershell
.\performance\Invoke-ChatContention.ps1 `
  -Manifest C:\secure\<manifest>.json `
  -Scenario focused `
  -RunId <runId> `
  -Rps 100 `
  -DurationSeconds 30 `
  -WarmupSeconds 2 `
  -SettleSeconds 120
```

집중과 분산 조건의 RPS, 시간, 메시지 크기, warmup/settle, 서버 설정을 동일하게 유지한다.

## 포트폴리오 문장

> 10개 클라이언트의 단일 방/10개 방 채팅 부하를 조건별 3회 비교해 7,200건의 전송·DB 커밋·구독자 수신 정합성을 검증했습니다. 5,005개 PostgreSQL 표본에서 차단 관계가 관측되지 않아 모임 행 잠금을 주 병목으로 단정하지 않고, 100건/s 단계 실험에서 양쪽 모두 발생한 수십 초 적체의 후속 분석 대상을 STOMP 채널과 커밋 후 fan-out 경로로 좁혔습니다.

100건/s 결과는 처리량 달성 표현에 사용하지 않는다. 실제 서버 처리율과 STOMP queue 체류 시간은 아직 별도 계측하지 않았으므로 `[측정값]`으로 남긴다.
