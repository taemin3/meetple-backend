# Outbox CDC 장애 복구 측정

staging의 `PostgreSQL outbox_events -> WAL -> Debezium -> Kafka -> 측정 Consumer` 경로를 검증합니다. Kafka Connect offset과 PostgreSQL replication slot은 유지하며 Connector를 잠시 `PAUSED`로 전환한 뒤 다시 `RUNNING`으로 복구합니다.

## 측정 결과

`performance/k6/scenarios/outbox-cdc-recovery.js`는 고유 `runId`를 만들고 다음 값을 출력합니다.

- `createdEvents`: 측정 API가 생성한 고유 eventId 수
- `committedOutboxEvents`: PostgreSQL `outbox_events`에 커밋된 해당 runId 행 수
- `mainTopicEvents`: 별도 측정 Consumer가 Main Topic에서 읽은 고유 eventId 수
- `duplicateMainTopicEvents`: Main Topic에서 같은 eventId를 추가로 읽은 횟수
- `cdcLatencyP50Ms`, `cdcLatencyP95Ms`, `cdcLatencyP99Ms`, `cdcLatencyMaxMs`: Outbox `occurredAt`부터 측정 Consumer 수신까지의 시간
- `catchUpDurationMs`: Connector가 다시 `RUNNING`이 된 뒤 전체 이벤트 수신을 확인하기까지의 시간

`OUTBOX_CDC_PAUSED`에서는 `committedOutboxEvents=100`, `mainTopicEvents=0`이어야 합니다. `OUTBOX_CDC_RECOVERED`에서는 `createdEvents`, `committedOutboxEvents`, `mainTopicEvents`, `successfulEvents`가 모두 100이고 `duplicateMainTopicEvents=0`이어야 합니다.

## 준비

1. staging backend에 `enable_push_retry_measurement=true`를 적용하고 배포합니다.
2. 측정 계정에 Push token 하나를 등록합니다. 실제 FCM 대신 측정 Sender의 성공 응답을 사용합니다.
3. `docs/operations/debezium-replication-slot-recovery.md`의 3절대로 Kafka Connect 8083 포트의 SSM tunnel을 `localhost:18083`에 엽니다.
4. 별도 PowerShell에서 아래 상태가 `RUNNING`인지 확인합니다.

```powershell
Invoke-RestMethod http://localhost:18083/connectors/meetple-outbox-connector/status |
  ConvertTo-Json -Depth 10
```

## 100건 실행

Connector 중단 시간은 60초이고 API 요청은 10 VU로 총 100건입니다. 테스트 중 일반 Outbox 이벤트도 DB에 정상 커밋되지만 Kafka 전달은 최대 약 60초 늦어집니다.

```powershell
Set-Item Env:K6_CDC_EVENT_COUNT "100"
Set-Item Env:K6_CDC_VUS "10"
Set-Item Env:K6_CDC_PAUSE_SECONDS "60"
Set-Item Env:K6_CONNECT_URL "http://localhost:18083"

k6 run .\performance\k6\scenarios\outbox-cdc-recovery.js 2>&1 |
  Tee-Object .\outbox-cdc-recovery-100.log
```

기존 staging k6 실행과 같이 `K6_BASE_URL`, `K6_ALLOW_REMOTE=true`, `K6_CONFIRM_TARGET`, `K6_EMAIL`, `K6_PASSWORD`도 같은 PowerShell 창에 설정되어 있어야 합니다.

스크립트는 `RUNNING 확인 -> PAUSE -> Outbox 100건 커밋 -> 60초 유지 -> RESUME -> 전량 수신 대기` 순서로 실행합니다. 오류가 발생해도 teardown의 `finally`에서 resume을 요청합니다.

## 비상 복구와 중단 기준

터미널을 강제로 닫았거나 Connector가 60초 안에 `RUNNING`으로 돌아오지 않으면 즉시 아래 명령을 실행합니다.

```powershell
Invoke-RestMethod `
  -Method Put `
  http://localhost:18083/connectors/meetple-outbox-connector/resume

Invoke-RestMethod `
  http://localhost:18083/connectors/meetple-outbox-connector/status |
  ConvertTo-Json -Depth 10
```

Connector 또는 task가 `FAILED`이면 새 이벤트 생성을 중단하고 다음 명령으로 실패 task만 재시작합니다.

```powershell
Invoke-RestMethod `
  -Method Post `
  'http://localhost:18083/connectors/meetple-outbox-connector/restart?includeTasks=true&onlyFailed=true'
```

검증 후 `enable_push_retry_measurement=false`로 되돌려 배포하고, 임시 8083 security group ingress와 SSM tunnel을 제거합니다.
