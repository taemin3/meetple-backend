# 3단계: Outbox 이벤트 생성 기반

## 범위

이 단계는 비즈니스 코드에서 사용할 공통 Outbox 저장 기반과 PostgreSQL에서 Kafka까지의 CDC 경로를 준비한다.

- `outbox_events` Flyway 마이그레이션
- 버전이 있는 Push 이벤트 envelope
- 기존 비즈니스 트랜잭션 참여를 강제하는 Outbox publisher
- Debezium PostgreSQL Connector 등록
- Debezium Outbox Event Router를 통한 Topic, key, header, payload 변환 검증

일반 알림과 채팅 메시지 생성 서비스에는 아직 publisher를 연결하지 않는다. 해당 연결은 각각 6단계와 7단계에서 수행한다.

## 저장 모델

`outbox_events`는 추가 전용 테이블이다. Debezium이 필요한 라우팅 메타데이터와 Consumer가 사용할 payload를 함께 저장한다.

| 컬럼 | 역할 |
| --- | --- |
| `id` | 이벤트 UUID이자 Kafka `id` header |
| `aggregate_type`, `aggregate_id` | 이벤트 원본 aggregate 식별 |
| `event_type` | 이벤트 종류 |
| `event_key` | Kafka record key (`member:{id}` 또는 `room:{id}`) |
| `topic` | 최종 Kafka Topic |
| `schema_version` | payload 계약 버전 |
| `payload` | Consumer가 읽을 JSONB envelope |
| `occurred_at` | 비즈니스 트랜잭션에서 이벤트를 만든 UTC 시각 |
| `deduplication_key` | 같은 비즈니스 사건의 중복 Outbox 생성 방지 |

`deduplication_key`에는 유일 제약이 있고, `schema_version`은 1 이상이어야 한다.

## Kafka 이벤트 계약

Kafka value는 다음 envelope 구조를 사용한다.

```json
{
  "eventId": "fc7b6d95-7dc4-49da-81c3-923c9ede7d44",
  "eventType": "PARTICIPATION_APPROVED",
  "schemaVersion": 1,
  "occurredAt": "2026-08-07T07:02:40.9665179Z",
  "aggregateType": "notification",
  "aggregateId": "101",
  "data": {
    "recipientMemberId": 7,
    "meetingId": 101
  }
}
```

Debezium Outbox Event Router는 다음과 같이 변환한다.

- 일반 알림: Topic `meetple.push.notification.v1`, key `member:{recipientMemberId}`
- 채팅 알림: Topic `meetple.push.chat.v1`, key `room:{roomId}`
- headers: `id`, `eventType`, `schemaVersion`, `aggregateType`, `aggregateId`, `deduplicationKey`

같은 key는 Kafka 기본 partitioner에서 같은 partition으로 전달되어 회원별 일반 알림 또는 채팅방별 메시지 순서를 유지한다.

## 트랜잭션 경계

`OutboxEventPublisher.publish()`는 `Propagation.MANDATORY`를 사용한다. 호출 전에 비즈니스 트랜잭션이 없으면 저장을 시작하지 않고 실패한다.

```text
비즈니스 데이터 INSERT/UPDATE
        +
outbox_events INSERT
        ↓
동일 PostgreSQL 트랜잭션 COMMIT 또는 ROLLBACK
```

따라서 비즈니스 데이터만 커밋되고 Outbox가 누락되거나, Outbox만 커밋되는 상태를 허용하지 않는다.

## Connector 등록과 상태 확인

`outbox_events` 마이그레이션 적용 후 Connector를 등록한다.

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8083/connectors `
  -ContentType 'application/json' `
  -InFile .\docker\debezium\connectors\meetple-outbox-connector.json

Invoke-RestMethod `
  -Uri http://localhost:8083/connectors/meetple-outbox-connector/status
```

Connector와 task가 모두 `RUNNING`이어야 한다.

## Kafka UI에서 이벤트 확인

로컬 Kafbat UI를 실행한다.

```powershell
docker-compose up -d kafka-ui
```

브라우저에서 `http://localhost:8089`에 접속한 뒤 다음 경로로 확인한다.

```text
meetple-local → Topics → 대상 Topic → Messages
```

- 일반 알림 Topic: `meetple.push.notification.v1`
- 채팅 알림 Topic: `meetple.push.chat.v1`
- 과거 메시지는 조회 시작 위치를 `Oldest` 또는 `Beginning`으로 설정

화면에서 partition, Kafka key, headers, JSON payload를 확인할 수 있다. `KAFKA_UI_PORT`를 변경하면 접속 포트도 함께 변경된다.

PostgreSQL의 `TIMESTAMP WITH TIME ZONE`은 Debezium에서 문자열형 `ZonedTimestamp`로 표현된다. Outbox Router의 `table.field.event.timestamp`에는 `INT64`가 필요하므로 이 옵션은 지정하지 않는다. Kafka record timestamp는 Debezium 이벤트 시간을 사용하고, 실제 이벤트 발생 시각은 DB의 `occurred_at`과 payload의 `occurredAt`에 보존한다.

## 이 단계에서 확인한 항목

- 이벤트 envelope와 row ID 일치
- Topic, key, schema version, deduplication key 저장
- 트랜잭션 밖 발행 거부
- 비즈니스 트랜잭션 롤백 시 Outbox 행도 롤백
- 중복 `deduplication_key` 거부
- Connector/task `RUNNING`
- logical replication slot `meetple_outbox` 활성화
- publication `meetple_outbox_publication`에 `public.outbox_events` 포함
- 커밋한 Outbox 이벤트가 의도한 Topic, key, headers, JSON payload로 발행됨

Kafka Consumer의 중복 소비 방지 ledger, FCM 전송, retry Topic과 DLQ는 4단계와 8단계에서 구현한다.
