# 채팅방 동시 전송 잠금 병목 측정

## 결론 상태

현재 코드는 동일 모임의 메시지 전송을 `meetings` 한 행의 비관적 쓰기 잠금으로 직렬화한다. 2026-09-12 로컬 비교에서는 20건/s와 100건/s 모두 PostgreSQL 잠금 대기 또는 차단 관계가 관측되지 않았고, 집중 조건만 반복적으로 느려지는 결과도 없었다. 따라서 측정한 환경에서는 이 잠금이 실제 처리량 또는 꼬리 지연의 주 병목이라는 가설이 지지되지 않았다.

이번 변경은 계측과 재현 도구만 추가하며 잠금 구조를 바꾸지 않는다. 상세 결과와 원시 파일 목록은 `chat-lock-contention-results-2026-09-12.md`에 기록한다.

## 현재 코드 재검증

- `ChatService.sendMessage()`는 `@Transactional`이다.
- `MeetingRepository.findByIdForUpdate()`는 `PESSIMISTIC_WRITE`와 `host` EntityGraph를 사용한다.
- 잠금 뒤 접근/전송 권한 확인, `clientMessageId` 중복 조회, 마지막 `roomSequence` 조회, `saveAndFlush`, 읽음 상태 갱신, 푸시 대상 조회, Outbox 저장을 수행한다.
- `ChatMessageFanOutEventListener`는 `AFTER_COMMIT`에서 로컬 STOMP 전달 후 Redis Pub/Sub 발행을 수행한다. controller 반환이나 STOMP 프레임 쓰기는 DB 저장·커밋·클라이언트 수신 성공 증거가 아니다.
- Flutter는 재전송에서 같은 `clientMessageId`를 사용하고, 수신 메시지를 `id/clientMessageId`로 중복 제거하며, `roomSequence` 정렬과 `afterSequence` 복구를 사용한다.

코드 근거:

- `ChatService.java`: `sendMessage()` 147행, 단계별 호출 166~220행, 순번/저장 291~306행, 잠금 조회 316행
- `MeetingRepository.java`: `findByIdForUpdate()` 153~157행
- `ChatWebSocketController.java`: STOMP SEND 진입 30~37행
- `ChatMessageFanOutEventListener.java`: `AFTER_COMMIT` 15~21행
- `ChatMessageFanOutService.java`: 로컬 STOMP 전달 32~42행
- 앱 `chat_room_page.dart`: `afterSequence` 413~444행, 중복 제거 490~520행, 발신 ID 574~591행

repository 시간에는 Hikari 커넥션 획득과 ORM/SQL 실행이 함께 포함된다. `lockLookup` 증가만으로 행 잠금 대기라고 단정하지 않고 같은 구간의 `wait_event_type = 'Lock'` 및 `pg_blocking_pids()`와 함께 판단한다. `readStateUpdate`와 `outboxSave`의 JPA `save()`는 SQL이 commit flush까지 지연될 수 있어 비용 일부가 `transactionCommit`에 포함될 수 있다.

## 계측

`MEETPLE_PERFORMANCE_CHAT_SEND_ENABLED=true`일 때만 `[CHAT-LOAD:<runId>]` 메시지를 수집한다. 기본값은 `false`이고 최대 20 run, run당 5,000 표본이다.

마이크로초 단위 p50/p95/p99/max와 개별 레코드를 제공한다.

- `lockLookup`, `duplicateLookup`, `sequenceLookup`, `messageSave`
- `readStateUpdate`, `pushRecipientLookup`, `outboxSave`
- `serviceBody`: 서비스 진입부터 반환 직전
- `transactionCommit`: 서비스 진입부터 실제 `afterCommit`

고부하 공통 적체를 분리하기 위해 realtime report도 함께 수집한다.

- `inboundAuth`: STOMP SEND의 Redis 토큰 상태 검증
- `inboundQueue`: inbound channel 등록부터 handler 실행 직전까지의 대기
- `localFanOut`: 커밋 후 `SimpMessagingTemplate.convertAndSend()` 호출
- `redisPublish`: 커밋 후 Redis Pub/Sub 발행
- `outboundAuth`: 구독자별 Redis 토큰 및 모임·참여 권한 재검증
- `outboundQueue`: 구독자별 outbound channel 대기

`outboundAuth.count`는 입력 메시지 수가 아니라 실제 구독자 전달 시도 수다. 10명이 구독한 3,000건 실행에서는 정상적으로 약 30,000 표본이 예상된다. JSON 결과의 `serverRealtime.phaseMicros`에 p50/p95/p99/max가 저장된다.

인증된 요청으로 조회/메모리 초기화한다.

```text
GET /api/v1/performance/chat-send/report?runId=<runId>
GET /api/v1/performance/chat-send/realtime-report?runId=<runId>
POST /api/v1/performance/chat-send/reset?runId=<runId>
```

로컬 Actuator `metrics`에서 Hikari active/pending, process/system CPU를 1초 간격으로 수집한다.

## 동일 조건 fixture

각 manifest는 정확히 10개 계정과 10개 전용 모임을 포함한다. 10명 모두 모든 방에 접근 가능해야 하며 방마다 host 1명 + APPROVED 9명, 동일 알림 설정·push device 수·기존 메시지 수를 맞춘다. 실제 FCM과 Kafka consumer는 끄되 Outbox 저장은 유지한다.

manifest는 비밀번호를 포함하므로 커밋하지 않는다. `performance/chat-load-manifest.example.json`을 로컬 보안 경로에 복사한다.

로컬 fixture는 다음 명령으로 자동 생성할 수 있다. 먼저 `-DryRun`으로 변경 범위를 확인한 뒤 승인 스위치를 붙인다.

```powershell
.\performance\New-ChatLoadFixture.ps1 `
  -DatasetId chat-focused-r1 `
  -ManifestPath C:\secure\chat-focused-r1.json `
  -DryRun

.\performance\New-ChatLoadFixture.ps1 `
  -DatasetId chat-focused-r1 `
  -ManifestPath C:\secure\chat-focused-r1.json `
  -AcknowledgeLocalDataCreation
```

이 스크립트는 현재 저장소의 로컬 PostgreSQL container에 회원 10명, 모임 10개, APPROVED 참여 90개를 만들고 무작위 테스트 비밀번호가 포함된 manifest를 출력한다. 기존 이메일/방 제목이 같은 datasetId로 발견되면 중단하며 기존 행을 수정하지 않는다.

모든 조건에서 10개 연결이 10개 방을 모두 구독한다. 집중은 첫 방만, 분산은 10개 방을 round-robin으로 사용하므로 활성 방의 회원/푸시 대상/구독자 수는 10/9/10으로 같다.

초기 데이터 동일성을 엄격히 유지하려면 `focused-r1`, `distributed-r1`부터 r3까지 동일하게 만든 6개 독립 fixture manifest를 쓴다. 기존 DB 전체 restore나 방 전체 자동 삭제는 사용하지 않는다.

## 실행

```powershell
$env:MEETPLE_PERFORMANCE_CHAT_SEND_ENABLED='true'
$env:PUSH_FCM_ENABLED='false'
$env:PUSH_KAFKA_CONSUMER_ENABLED='false'
$env:SPRING_PROFILES_ACTIVE='local'
.\gradlew.bat bootRun
```

별도 터미널에서 잠금 표본을 수집하고 조건 종료 직후 `Ctrl+C`로 멈춘다.

```powershell
.\performance\Capture-ChatPostgresLocks.ps1 `
  -OutputPath .\performance\results\chat-contention\focused-r1-locks.jsonl
```

먼저 2건/s × 10초 smoke를 수행한다.

```powershell
.\performance\Invoke-ChatContention.ps1 `
  -Manifest C:\secure\chat-focused-r1.json `
  -Scenario focused -Smoke -RunId chat-focused-smoke
```

예정량=실제 전송량=DB 커밋량=고유 수신량이고 오류/미전송/미저장/저장 후 미수신이 모두 0이면 본 실험으로 간다.

```powershell
.\performance\Invoke-ChatContention.ps1 `
  -Manifest C:\secure\chat-focused-r1.json `
  -Scenario focused -RunId chat-focused-r1

.\performance\Invoke-ChatContention.ps1 `
  -Manifest C:\secure\chat-distributed-r1.json `
  -Scenario distributed -RunId chat-distributed-r1
```

기본은 조건별 20건/s × 60초 = 1,200건이다. 절대 시각 기반 전송 일정으로 `scheduled`, `attempted`, `unsent`, `scheduleLagMs`를 기록한다. 반복별 새 fixture 쌍으로 3회 실행한다.

20건/s에서 잠금 대기가 관측되지 않으면 다른 조건을 바꾸지 않고 RPS만 단계적으로 높인다.

```powershell
.\performance\Invoke-ChatContention.ps1 `
  -Manifest C:\secure\chat-focused-step50.json `
  -Scenario focused -RunId chat-focused-step50 `
  -Rps 50 -DurationSeconds 30 -WarmupSeconds 10 `
  -SettleSeconds 120
```

`SettleSeconds`는 전송 종료 후 DB 커밋과 전체 구독자 수신을 기다리는 상한이다. 목표 RPS에서 서버 적체가 발생하면 같은 값으로 집중/분산 조건을 비교하고, 상한 전에 모든 메시지가 관측되면 즉시 종료한다.

`Inspect-ChatLoadRun.ps1`은 삭제 없이 run 메시지와 연결 Outbox ID를 조회한다.

```powershell
.\performance\Inspect-ChatLoadRun.ps1 -RunId chat-focused-r1
```

합성 데이터 삭제는 결과 검토 후에만 실행한다. `Remove-ChatLoadRun.ps1`은 정확한 run 표식 메시지와 그 메시지 ID를 aggregate로 가진 Outbox만 삭제한다. 기존에 존재했을 수 있는 읽음 상태는 고유한 run 소유권을 증명할 수 없어 보존한다. 따라서 반복 사이 초기화가 아니라 최종 정리에만 사용한다.

```powershell
.\performance\Inspect-ChatLoadRun.ps1 -RunId chat-focused-r1
.\performance\Remove-ChatLoadRun.ps1 `
  -RunId chat-focused-r1 `
  -AcknowledgeSyntheticDataDeletion
```

## 결과 표와 판정

| 조건 | 반복 | attempted/1200 | unsent | DB committed | any/all-10 received | E2E p50/p95/p99 | lock p50/p95/p99 | tx p50/p95/p99 | Lock wait | Hikari active/pending | CPU |
|---|---:|---:|---:|---:|---:|---|---|---|---:|---|---|
| 집중 | 1~3 | 회차별 1200/1200 | 0 | 3,600 | 3,600/3,600 | 37.44/109.95/434.27ms | p95 2.87ms | p95 23.89ms | 0/2,511 표본 | max 1/0 | avg 3.80% |
| 분산 | 1~3 | 회차별 1200/1200 | 0 | 3,600 | 3,600/3,600 | 36.75/117.44/319.57ms | p95 3.00ms | p95 23.24ms | 0/2,494 표본 | max 1/0 | avg 3.75% |

1. 집중 조건의 lock/E2E/transaction p95·p99가 반복적으로 증가하고 같은 시간대에 Lock wait와 blocker가 관측될 때만 모임 행 경합 가설을 지지한다.
2. 집중만 느리지만 Lock wait가 없으면 Hikari, CPU, commit flush, STOMP outbound, Redis를 추가 분리한다.
3. 두 조건이 비슷하면 인증 Redis, DB pool, Outbox flush, fan-out 같은 공통 경로를 우선 본다.
4. 미전송 또는 schedule lag가 크면 부하 발생기 한계일 수 있어 해당 반복을 폐기한다.
5. 카운터 행 분리만으로 동일 방 순번 직렬화가 사라진다고 주장하지 않는다.

Hikari pending 또는 CPU가 함께 치솟아 원인이 불명확할 때만 같은 부하 조건에서 JFR을 추가한다. 서버 PID를 확인한 뒤 측정 구간을 포함하는 90초 profile을 남기고, 조건 간 JVM 옵션과 heap을 바꾸지 않는다.

```powershell
jcmd <backend-pid> JFR.start name=chat-lock settings=profile duration=90s `
  filename=.\performance\results\chat-contention\chat-lock.jfr
```

## 현재 검증 경계

- Java 21 + Gradle 9.4.1 `ChatServiceTest`: 성공
- 계측 단위 테스트 `ChatSendMeasurementRecorderTest`: 성공
- 전체 `gradlew.bat test`: 469개 중 29개 실패, 6개 skip. 실패는 Docker/Redis 미실행으로 인한 기존 `MemberControllerTest`, `PushDeviceTokenControllerTest`, `SecurityConfigTest`의 `RedisConnectionFailureException`에 한정
- Node.js 24 `node --check performance/chat-load.mjs`: 성공
- `git diff --check`: 성공
- 로컬 PostgreSQL/Redis/backend smoke: 집중·분산 각 20/20건 전송·커밋·수신
- 독립 fixture 20건/s × 60초: 집중·분산 각 3회, 총 7,200건 전송·커밋·수신
- 독립 fixture 100건/s × 30초: 집중·분산 각 1회, 총 6,000건 전송·커밋·수신. 약 96~106초 p95 수신 적체 발생
- STOMP 단계별 독립 fixture 100건/s × 30초: 집중 E2E/inbound queue/outbound queue p95 `99.78/74.25/75.03초`, 분산 `102.58/77.81/77.98초`. 양쪽 모두 outbound 인증 30,000회, Hikari active 최대 1·pending 0
- STOMP 단계별 후속 실행과 같은 시간대의 PostgreSQL 잠금 표본은 미수집. 잠금 판단은 앞선 100건/s 실행의 집중 0/1,766, 분산 0/1,611 차단 표본과 함께 수행
- AWS/staging, 클라우드 생성, 실제 FCM: 수행하지 않음

## 포트폴리오 문장

측정 전:

> 단일 채팅방 집중과 10개 방 분산 부하를 비교하도록 트랜잭션 단계·커밋·클라이언트 수신·PostgreSQL 차단 관계를 계측하고, 잠금 병목 가설을 검증 가능한 실험으로 설계했습니다.

잠금 경합이 증명된 경우에만:

> 10개 클라이언트의 20건/s 채팅 부하를 조건별 3회 비교해 단일 방 집중 시 잠금 대기 `[측정값]`와 E2E p99 `[측정값]ms`를 확인하고, 분산 대비 `[측정값]%` 꼬리 지연 증가 원인을 규명했습니다.

실제 측정 결과:

> 10개 클라이언트의 단일 방/10개 방 채팅 부하로 7,200건의 전송·DB 커밋·수신 정합성과 5,005개 PostgreSQL 차단 표본을 검증했습니다. 100건/s 후속 실험에서 양쪽 모두 STOMP queue p95 74~78초와 구독자별 outbound 인증 30,000회를 관측해, 모임 행 잠금이 아닌 실시간 fan-out 처리 경로로 병목 범위를 좁혔습니다.
