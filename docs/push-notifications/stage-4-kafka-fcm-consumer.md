# 4단계: Kafka Consumer와 FCM 발송

## 범위

이번 단계는 Kafka에 도착한 Push 이벤트를 소비해 회원의 등록 기기로 FCM을 발송하는 백엔드 기반을 제공한다.

- FCM 기기 토큰 등록, 갱신, 삭제 API
- 한 회원의 여러 기기 지원
- 단일 로그아웃 시 해당 기기 토큰 삭제
- 전체 로그아웃 시 회원의 모든 기기 토큰 삭제
- 일반 알림과 채팅 알림 Kafka Consumer
- Firebase Admin SDK 기반 multicast 발송
- 만료된 FCM 토큰 제거
- 이벤트와 기기 단위 발송 ledger

Flutter의 알림 권한 요청, token refresh 전달, foreground/background/terminated 수신과 클릭 이동은 5단계에서 구현한다. 비즈니스 서비스의 Outbox 연결은 일반 알림 6단계, 채팅 알림 7단계에서 구현한다. Retry Topic과 DLQ는 8단계 범위다.

## 로컬 설정

Firebase 서비스 계정 JSON은 저장소 밖에 보관하고 Application Default Credentials로 읽는다.

```properties
GOOGLE_APPLICATION_CREDENTIALS=C:/secure/path/firebase-adminsdk.json
PUSH_FCM_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
PUSH_KAFKA_CONSUMER_ENABLED=true
PUSH_KAFKA_CONSUMER_GROUP=meetple-push-fcm-v1
PUSH_KAFKA_CONSUMER_CONCURRENCY=3
```

서비스 계정 JSON, private key, 실제 FCM token은 Git에 추가하지 않는다. Consumer를 켰는데 FCM이 꺼져 있거나 ADC를 읽을 수 없으면 애플리케이션 시작을 실패시켜 메시지를 조용히 버리는 상태를 허용하지 않는다.

## 기기 토큰 API

### 등록 또는 갱신

```http
POST /api/v1/push/device-tokens
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "deviceId": "앱 설치 단위 UUID",
  "token": "FCM registration token",
  "platform": "ANDROID"
}
```

- `deviceId`와 token hash에는 각각 유일 제약이 있다.
- 같은 설치에서 token이 갱신되면 기존 행을 갱신한다.
- 같은 설치에서 다른 회원이 로그인하면 토큰 소유권을 현재 인증 회원으로 옮긴다.
- 한 회원은 여러 `deviceId` 행을 가질 수 있다.
- token 원문은 FCM 발송에만 사용하고 응답이나 로그에 노출하지 않는다.

### 특정 기기 삭제

```http
DELETE /api/v1/push/device-tokens/{deviceId}
Authorization: Bearer {accessToken}
```

다른 회원 소유의 `deviceId`는 삭제하지 않는다.

### 로그아웃 연동

단일 로그아웃 요청은 기존 계약과 호환되며 `deviceId`를 선택적으로 받는다.

```json
{
  "refreshToken": "...",
  "deviceId": "앱 설치 단위 UUID"
}
```

`deviceId`가 있으면 해당 회원과 기기에 해당하는 토큰만 삭제한다. `/api/v1/auth/logout-all`은 회원의 모든 기기 토큰을 삭제한다.

## Kafka 이벤트 계약

공통 envelope의 `schemaVersion`은 현재 `1`만 지원한다. 지원하지 않는 버전이나 필수 필드가 없는 이벤트는 FCM을 발송하지 않고 Consumer 예외로 처리한다.

### 일반 알림 data

```json
{
  "recipientMemberId": 7,
  "notificationId": 501,
  "meetingId": 101,
  "title": "참여가 승인됐어요",
  "body": "모임 참여 신청이 승인됐습니다."
}
```

FCM data에는 `eventId`, `eventType`, `schemaVersion`, `route=MEETING_DETAIL`, `notificationId`, `meetingId`를 넣는다.

### 채팅 알림 data

```json
{
  "recipientMemberIds": [7, 8],
  "senderMemberId": 6,
  "senderNickname": "보낸 사람",
  "roomId": 55,
  "chatMessageId": 9001,
  "roomSequence": 30,
  "title": "러닝 모임",
  "body": "곧 도착합니다."
}
```

- Consumer에서도 방어적으로 `senderMemberId`를 수신자에서 제외한다.
- `route=CHAT_ROOM`과 `roomId`를 FCM data로 전달한다.
- Android collapse key와 notification tag는 `chat-room-{roomId}`를 사용한다.
- 현재 채팅방을 보고 있을 때 표시를 생략하는 판단은 5단계 Flutter foreground 처리에서 한다.

## 중복 소비 경계

`push_event_deliveries`는 `(event_id, device_token_id)`를 유일 키로 사용한다.

- `SENT`: 같은 이벤트가 재소비돼도 해당 기기에는 다시 발송하지 않는다.
- `INVALID_TOKEN`: FCM `UNREGISTERED` 응답을 받은 기기는 토큰을 삭제하고 다시 발송하지 않는다.
- `FAILED`: Kafka 재전달 시 실패한 기기만 다시 시도한다.

Kafka offset commit과 FCM 외부 호출은 하나의 원자적 트랜잭션으로 묶을 수 없다. FCM 성공 직후 프로세스가 종료되고 ledger 반영 전에 중단되는 작은 중복 가능 구간은 남는다. `eventId`를 앱에도 전달하므로 5단계에서 앱의 표시 중복 방지 키로 사용한다.

## 8단계 적용 후 실패 처리

Record 단위 ack와 비차단 Retry Topic을 사용한다. 일시 오류는 1초, 10초, 100초, 300초 backoff로 재시도하고 최초 소비를 포함해 최대 5번 처리한 뒤 DLQ로 이동한다. 전체 재시도 구간을 5분 claim lease보다 길게 유지해 프로세스 종료나 DB 일시 오류로 claim이 남아도 만료 후 자동 복구할 기회를 보장한다. 잘못된 JSON, 지원하지 않는 schema version과 같은 영구 계약 오류는 재시도 없이 바로 DLQ로 이동한다.

Retry Topic 소비 시에는 레코드의 현재 Topic이 아니라 `kafka_original-topic` 헤더를 이벤트 계약 선택에 사용한다. DLQ에는 원본 Topic, partition, offset과 예외 정보가 헤더로 보존된다.

상세 Topic 목록, 운영 확인과 재처리 방법은 [8단계 문서](./stage-8-retry-dlq.md)를 따른다.

## 만료 토큰 정리

Firebase Admin SDK 응답 중 `MessagingErrorCode.UNREGISTERED`인 token만 만료된 것으로 판단해 삭제한다. `INVALID_ARGUMENT`는 payload 오류일 수도 있으므로 token 삭제 근거로 사용하지 않는다.
