# 2단계 로컬 CDC 인프라

## 범위

이 단계는 PostgreSQL logical replication, Kafka, Kafka Connect와 Debezium PostgreSQL Connector 실행 환경만 준비한다.

- PostgreSQL: `postgis/postgis:16-3.4`, `wal_level=logical`
- Kafka: `apache/kafka:4.3.1`, 단일 노드 KRaft
- Kafka Connect: `quay.io/debezium/connect:3.6.0.Final`
- 일반 알림 Topic: `meetple.push.notification.v1`
- 채팅 알림 Topic: `meetple.push.chat.v1`
- Topic 파티션: 로컬 기본값 3, 복제 계수 1

Outbox 테이블과 백엔드 이벤트 생성 코드는 3단계 범위다. 따라서 이 단계에서는 Kafka Connect의 Debezium 플러그인 로딩까지만 검증하고 Connector 등록은 3단계 Outbox 테이블 생성 후 수행한다.

## 실행

저장소 루트의 `.env.example`을 참고해 로컬 `.env`를 준비한 뒤 실행한다.

```powershell
docker-compose up -d postgres redis kafka kafka-init kafka-connect
```

기본 접근 주소는 다음과 같다.

- PostgreSQL: `localhost:15432`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`
- Kafka Connect REST API: `http://localhost:8083`

## 검증

PostgreSQL logical replication 설정을 확인한다.

```powershell
docker-compose exec postgres psql -U $env:POSTGRES_USER -d meetple -c "SHOW wal_level; SHOW max_wal_senders; SHOW max_replication_slots; SHOW max_slot_wal_keep_size;"
```

Kafka Topic을 확인한다.

```powershell
docker-compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --describe
```

Kafka Connect와 PostgreSQL Connector 플러그인을 확인한다.

```powershell
Invoke-RestMethod http://localhost:8083/connectors
Invoke-RestMethod http://localhost:8083/connector-plugins
```

## 3단계 연결 지점

3단계에서 `public.outbox_events` 테이블을 만든 뒤 다음 설정을 Kafka Connect REST API에 등록한다.

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8083/connectors `
  -ContentType 'application/json' `
  -InFile .\docker\debezium\connectors\meetple-outbox-connector.json
```

Connector는 `topic` 컬럼을 최종 Topic 이름으로, `event_key`를 Kafka record key로 사용한다. Outbox Event Router가 `payload` JSON을 메시지 값으로 전달하며 이벤트 ID와 계약 메타데이터는 Kafka header에도 포함한다.

현재 DB 계정 재사용은 로컬 개발 환경 전용이다. 운영 환경에서는 `REPLICATION` 권한과 Outbox 테이블 조회 권한만 가진 전용 Debezium 계정을 별도로 사용해야 한다. 단일 Kafka broker와 복제 계수 1도 로컬 환경에만 적용한다.
