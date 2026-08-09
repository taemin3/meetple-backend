# 8단계: Push Retry Topic과 DLQ

## 범위

이번 변경은 Kafka Push Consumer의 실패 격리와 운영 확인을 담당한다.

- 비차단 Retry Topic 기반 지수 backoff
- 영구 오류와 일시 오류 분리
- 최대 시도 횟수 제한과 DLQ 이동
- DLQ 원본 레코드 위치와 예외 정보 로깅
- 기존 `push_event_deliveries` ledger를 이용한 재소비 멱등성 유지
- Embedded Kafka 통합 테스트

앱에서 같은 `eventId`의 로컬 알림 표시를 중복 방지하는 작업은 별도 앱 PR로 진행한다.

## 처리 흐름

```text
main topic
  -> 실패: retry-0 (1초)
  -> 실패: retry-1 (2초)
  -> 실패: retry-2 (4초)
  -> 실패: DLQ
```

최초 소비를 포함해 최대 4번 처리한다. Retry Topic으로 레코드를 옮긴 뒤 원본 Topic의 offset을 진행하므로 하나의 poison event가 원본 partition의 뒤 레코드를 계속 막지 않는다.

Spring Kafka의 비차단 Retry Topic은 원본 Topic의 순서를 보장하지 않는다. Push는 PostgreSQL의 비즈니스 데이터가 원본이고, 전송 ledger가 이벤트·기기 단위 중복 발송을 막으므로 이 트레이드오프를 허용한다. Redis Pub/Sub WebSocket 채팅 전달 경로에는 이 설정을 적용하지 않는다.

## Topic

일반 알림과 채팅 알림에 각각 같은 구조를 사용한다.

```text
meetple.push.notification.v1
meetple.push.notification.v1.retry-0
meetple.push.notification.v1.retry-1
meetple.push.notification.v1.retry-2
meetple.push.notification.v1.dlq

meetple.push.chat.v1
meetple.push.chat.v1.retry-0
meetple.push.chat.v1.retry-1
meetple.push.chat.v1.retry-2
meetple.push.chat.v1.dlq
```

Retry Topic과 DLQ의 partition 수는 원본 Topic과 같아야 한다. 로컬 환경은 `KAFKA_PUSH_TOPIC_PARTITIONS` 값을 모든 Topic에 동일하게 적용한다.

## 오류 분류

다음 계약 오류는 재시도해도 결과가 바뀌지 않으므로 `NonRetryablePushEventException`으로 분류하고 바로 DLQ로 보낸다.

- 올바르지 않은 JSON
- 필수 이벤트 메타데이터 누락
- 지원하지 않는 `schemaVersion`
- 잘못된 data 타입 또는 필수 필드
- 지원하지 않는 원본 Topic

FCM 일시 실패, 전송 ledger의 활성 claim 충돌, DB 일시 실패 등은 Retry Topic을 거친다.

## 원본 Topic 복원

Retry Topic에서 받은 `ConsumerRecord.topic()`은 `meetple.push.*.retry-N`이다. 이벤트 계약 선택에는 Spring Kafka가 보존한 `KafkaHeaders.ORIGINAL_TOPIC`을 사용한다. 이 헤더가 없는 최초 소비에서만 현재 레코드 Topic을 사용한다.

## 멱등성과 부분 성공

`push_event_deliveries`의 `(event_id, device_token_id)` unique ledger를 그대로 사용한다.

- `SENT`, `INVALID_TOKEN`: 재소비 시 다시 보내지 않는다.
- `FAILED`: 해당 기기만 다시 claim하고 전송한다.
- 전송 중인 claim: 다른 Consumer가 동시에 같은 이벤트를 보내지 않도록 재시도한다.

따라서 한 기기 전송에 성공하고 다른 기기가 실패한 뒤 Retry Topic에서 다시 소비해도 성공한 기기로는 중복 발송하지 않는다.

## DLQ 운영 확인

Kafka UI의 `Topics`에서 다음 Topic을 확인한다.

- `meetple.push.notification.v1.dlq`
- `meetple.push.chat.v1.dlq`

DLQ 레코드에는 원본 key와 value가 유지되고 다음 헤더가 포함된다.

- `kafka_original-topic`
- `kafka_original-partition`
- `kafka_original-offset`
- `kafka_exception-fqcn`
- `kafka_exception-cause-fqcn`
- `kafka_exception-message`
- `retry_topic-attempts`

Consumer 로그에는 payload나 FCM token을 기록하지 않고 DLQ 위치, 원본 위치, 예외 타입과 메시지만 기록한다.

재처리는 원인을 수정한 뒤 DLQ 레코드의 key와 value를 원본 Topic에 다시 발행한다. 같은 `eventId`를 유지해야 ledger가 이미 성공한 기기를 제외한다.

## 로컬 적용

기존 Kafka volume을 유지한 상태에서는 `kafka-init`을 다시 실행해 Topic을 추가한다.

```powershell
docker compose run --rm kafka-init
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server kafka:29092 `
  --list
```

backoff는 다음 환경 변수로 조정할 수 있다. Topic 수와 맞물리는 최대 시도 횟수는 4회로 고정한다.

```properties
PUSH_KAFKA_RETRY_INITIAL_DELAY_MS=1000
PUSH_KAFKA_RETRY_MULTIPLIER=2.0
PUSH_KAFKA_RETRY_MAX_DELAY_MS=4000
```

## 검증

`PushKafkaRetryIntegrationTest`는 Embedded Kafka에서 다음을 확인한다.

- 일시 오류가 Retry Topic을 거쳐 세 번째 처리에서 성공
- 영구 오류가 한 번만 처리된 뒤 곧바로 DLQ 이동
- DLQ에 원본 Topic, partition, offset과 원인 예외 헤더 보존
