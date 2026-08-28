# Debezium replication slot 복구 절차

Meetple staging의 `Outbox -> Debezium -> Kafka -> Consumer` 경로에서 PostgreSQL replication slot이 `unreserved` 또는 `lost`가 되었을 때 사용하는 운영 절차입니다.

`lost` slot은 ECS task나 Debezium connector를 재시작하는 것만으로 복구되지 않습니다. 기존 Outbox 이벤트가 재전송될 수 있으므로 아래 사전 확인 없이 slot이나 Kafka Connect offset을 초기화하지 않습니다.

## 장애 신호

- Slack/CloudWatch의 `meetple-staging-rds-replication-slot-lag-high` ALARM
- Slack/CloudWatch의 `meetple-staging-debezium-failed` ALARM
- Kafka Connect source task가 `FAILED` 또는 `RESTARTING`
- Debezium 로그의 `Unable to obtain valid replication slot`

## 1. slot과 재처리 범위 확인

RDS에 SSM port forwarding으로 접속한 뒤 다음 쿼리를 실행합니다.

```sql
SELECT
    slot_name,
    slot_type,
    active,
    active_pid,
    restart_lsn,
    confirmed_flush_lsn,
    wal_status,
    safe_wal_size,
    conflicting
FROM pg_replication_slots
WHERE slot_name = 'meetple_outbox';
```

- `reserved`, `extended`: slot은 아직 사용할 수 있습니다. Debezium 상태와 원인을 먼저 확인합니다.
- `unreserved`: 다음 checkpoint에서 필요한 WAL이 제거될 수 있으므로 즉시 원인을 확인합니다.
- `lost`: 필요한 WAL이 이미 제거되어 slot 재생성이 필요합니다.

재생성 전에 초기 snapshot으로 다시 발행될 Outbox 이벤트를 확인합니다.

```sql
SELECT
    topic,
    event_type,
    COUNT(*) AS event_count,
    MIN(occurred_at) AS oldest_event,
    MAX(occurred_at) AS newest_event
FROM outbox_events
GROUP BY topic, event_type
ORDER BY event_count DESC, topic, event_type;
```

재전송 시 이메일, push, 이미지 삭제 consumer의 멱등성과 이벤트 유효기간을 검토합니다. 재처리 영향이 불명확하면 복구를 중단하고 이벤트별 처리 방식을 먼저 결정합니다.

## 2. Kafka Connect 관리 포트에 임시 접속

다음 명령은 `infra/terraform`에서 실행합니다. 로컬 AWS profile 이름은 환경에 맞게 바꿉니다.

```powershell
$env:AWS_PROFILE = "meetple-deploy"
$env:AWS_REGION = "ap-northeast-2"

$ClusterName = terraform.exe output -raw ecs_cluster_name
$EventRuntimeService = terraform.exe output -raw event_runtime_service_name
$EcsInstancesSg = terraform.exe output -raw ecs_instance_security_group_id
$EventRuntimeSg = terraform.exe output -raw event_runtime_security_group_id

$TaskArn = aws ecs list-tasks `
  --cluster $ClusterName `
  --service-name $EventRuntimeService `
  --desired-status RUNNING `
  --query 'taskArns[0]' `
  --output text

$EventRuntimeEni = aws ecs describe-tasks `
  --cluster $ClusterName `
  --tasks $TaskArn `
  --query "tasks[0].attachments[?type=='ElasticNetworkInterface'].details[?name=='networkInterfaceId'].value | [0]" `
  --output text

$EventRuntimeIp = aws ec2 describe-network-interfaces `
  --network-interface-ids $EventRuntimeEni `
  --query 'NetworkInterfaces[0].PrivateIpAddress' `
  --output text

$ContainerInstanceArn = aws ecs list-container-instances `
  --cluster $ClusterName `
  --status ACTIVE `
  --query 'containerInstanceArns[0]' `
  --output text

$InstanceId = aws ecs describe-container-instances `
  --cluster $ClusterName `
  --container-instances $ContainerInstanceArn `
  --query 'containerInstances[0].ec2InstanceId' `
  --output text

```

Kafka Connect의 8083 포트는 기본적으로 열려 있지 않습니다. SSM 대상 EC2 security group에서만 임시 접근을 허용합니다. `0.0.0.0/0`으로 열지 않습니다.

```powershell
aws ec2 authorize-security-group-ingress `
  --group-id $EventRuntimeSg `
  --protocol tcp `
  --port 8083 `
  --source-group $EcsInstancesSg
```

새 PowerShell 창에서 tunnel을 열고 복구가 끝날 때까지 유지합니다.

```powershell
aws ssm start-session `
  --target $InstanceId `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=$EventRuntimeIp,portNumber=8083,localPortNumber=18083" `
  --region $env:AWS_REGION `
  --profile $env:AWS_PROFILE
```

## 3. connector 중지와 offset 초기화

다른 PowerShell 창에서 connector 상태를 확인합니다.

```powershell
Invoke-RestMethod `
  http://localhost:18083/connectors/meetple-outbox-connector/status |
  ConvertTo-Json -Depth 10
```

Connector를 중지한 뒤 `connector.state`가 `STOPPED`인지 확인합니다.

```powershell
Invoke-RestMethod `
  -Method Put `
  http://localhost:18083/connectors/meetple-outbox-connector/stop

Start-Sleep -Seconds 3

Invoke-RestMethod `
  http://localhost:18083/connectors/meetple-outbox-connector/status |
  ConvertTo-Json -Depth 10
```

초기 snapshot을 다시 수행할 수 있도록 framework-managed source offset을 초기화합니다.

```powershell
Invoke-RestMethod `
  -Method Delete `
  http://localhost:18083/connectors/meetple-outbox-connector/offsets

Invoke-RestMethod `
  http://localhost:18083/connectors/meetple-outbox-connector/offsets |
  ConvertTo-Json -Depth 10
```

`offsets`가 빈 배열인지 확인합니다.

## 4. lost slot 재생성과 connector 복구

Connector가 `STOPPED`이고 slot이 `active = false`인 경우에만 RDS에서 사용할 수 없는 slot을 삭제합니다.

```sql
SELECT pg_drop_replication_slot('meetple_outbox');
```

임의의 PostgreSQL PID를 먼저 종료하지 않습니다. slot 삭제가 active session 때문에 실패하면 Connector 상태와 `pg_replication_slots.active_pid`를 다시 확인합니다.

Connector 설정을 삭제하면 ECS task의 `connector-manager`가 저장소의 설정으로 30초 안에 재생성합니다.

```powershell
Invoke-RestMethod `
  -Method Delete `
  http://localhost:18083/connectors/meetple-outbox-connector

Start-Sleep -Seconds 40

Invoke-RestMethod `
  http://localhost:18083/connectors/meetple-outbox-connector/status |
  ConvertTo-Json -Depth 10
```

`connector.state`와 `tasks[0].state`가 모두 `RUNNING`인지 확인합니다.

RDS에서도 새 slot이 정상적으로 사용되는지 확인합니다.

```sql
SELECT
    slot_name,
    active,
    restart_lsn,
    confirmed_flush_lsn,
    wal_status
FROM pg_replication_slots
WHERE slot_name = 'meetple_outbox';
```

정상 기준은 `active = true`, 두 LSN 값 존재, `wal_status = reserved`입니다. 마지막으로 staging에서 새 이벤트를 한 건 생성해 실제 consumer 처리까지 검증하고 Slack 알람이 `OK`로 복구되는지 확인합니다.

## 5. 임시 접근 제거

복구 성공 여부와 관계없이 임시 8083 ingress를 제거합니다.

```powershell
aws ec2 revoke-security-group-ingress `
  --group-id $EventRuntimeSg `
  --protocol tcp `
  --port 8083 `
  --source-group $EcsInstancesSg
```

명령 결과의 `Return`이 `true`인지 확인하고 18083 SSM tunnel을 `Ctrl+C`로 종료합니다. RDS 확인용 tunnel도 더 이상 필요하지 않으면 종료합니다.

## 재발 방지 확인

- `meetple-staging-rds-replication-slot-lag-high`가 `OK`인지 확인합니다.
- `meetple-staging-debezium-failed`가 반복되지 않는지 확인합니다.
- Dashboard의 `OldestReplicationSlotLag`와 `TransactionLogsDiskUsage` 추이를 확인합니다.
- WAL 제한을 늘리는 것만으로 장애 원인을 해결하지 않습니다. Debezium task 실패, EC2 자원 부족, RDS 연결 문제를 함께 확인합니다.
