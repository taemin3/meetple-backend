# Debezium replication slot 복구 절차

Meetple staging의 `Outbox -> Debezium -> Kafka -> Consumer` 경로에서 PostgreSQL replication slot 이상을 진단하고 복구할 때 사용하는 운영 절차입니다.

`unreserved` slot은 필요한 WAL이 남아 있으면 따라잡을 수 있지만, `lost` slot은 ECS task나 Debezium connector를 재시작하는 것만으로 복구되지 않습니다. 기존 Outbox 이벤트가 재전송될 수 있으므로 `lost` 확인과 재처리 영향 검토 없이 slot이나 Kafka Connect offset을 초기화하지 않습니다.

## 장애 신호

- Slack/CloudWatch의 `meetple-staging-rds-replication-slot-lag-high` ALARM
- Slack/CloudWatch의 `meetple-staging-debezium-failed` ALARM
- Kafka Connect source task가 `FAILED` 또는 `RESTARTING`
- Debezium 로그의 `Unable to obtain valid replication slot`

## 1. staging workspace와 RDS 접속 확인

다음 명령은 `infra/terraform`에서 실행합니다. 파괴적인 후속 명령이 production을 가리키지 않도록 output 조회 전에 workspace를 검증합니다.

```powershell
$env:AWS_PROFILE = "meetple-deploy"
$env:AWS_REGION = "ap-northeast-2"

$Workspace = terraform.exe workspace show
if ($Workspace.Trim() -ne "staging") {
  throw "staging workspace에서만 실행할 수 있습니다. 현재 workspace: $Workspace"
}

$ClusterName = terraform.exe output -raw ecs_cluster_name
$DbEndpoint = terraform.exe output -raw rds_endpoint
$DbHost = ($DbEndpoint -split ':')[0]
$DbSecretArn = terraform.exe output -raw rds_master_secret_arn

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

if ([string]::IsNullOrWhiteSpace($InstanceId) -or $InstanceId -eq "None") {
  throw "SSM tunnel에 사용할 ECS EC2 instance를 찾지 못했습니다."
}
```

같은 PowerShell 창에서 RDS 관리형 secret을 먼저 조회합니다. 비밀번호를 화면이나 문서에 출력하지 않고 clipboard로만 전달합니다.

```powershell
$DbSecret = aws secretsmanager get-secret-value `
  --secret-id $DbSecretArn `
  --query SecretString `
  --output text `
  --region $env:AWS_REGION `
  --profile $env:AWS_PROFILE |
  ConvertFrom-Json

$DbSecret.username
Set-Clipboard -Value ([string]$DbSecret.password)
```

이어서 같은 PowerShell 창에서 RDS tunnel을 열고 진단이 끝날 때까지 유지합니다.

```powershell
aws ssm start-session `
  --target $InstanceId `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=$DbHost,portNumber=5432,localPortNumber=15432" `
  --region $env:AWS_REGION `
  --profile $env:AWS_PROFILE
```

IntelliJ Database에서 PostgreSQL data source를 다음과 같이 설정합니다.

```text
Host: localhost
Port: 15432
Database: meetple
User: 위 명령이 출력한 username
Password: clipboard에 복사된 값
```

## 2. slot 상태와 재처리 범위 확인

RDS에 연결한 IntelliJ console에서 다음 쿼리를 실행합니다.

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

- `reserved`, `extended`: slot은 사용할 수 있습니다. offset을 초기화하거나 slot을 삭제하지 않고 Debezium 상태와 지연 원인을 확인합니다.
- `unreserved`: 다음 checkpoint에서 WAL이 제거될 위험이 있지만 아직 따라잡을 수 있습니다. offset을 초기화하거나 slot을 삭제하지 않고 Connector를 복구해 따라잡게 합니다.
- `lost`: 필요한 WAL이 이미 제거되었습니다. 재처리 범위를 검토한 뒤에만 이 문서의 `lost` 전용 복구를 수행합니다.

`unreserved` 또는 `lost`가 되기 전에 현재 DB에 적용된 한도도 확인합니다.

```sql
SHOW max_slot_wal_keep_size;
```

Terraform의 `rds_max_slot_wal_keep_size_mb` 변경은 RDS 재부팅 전까지 적용되지 않습니다. `SHOW` 결과가 새 값으로 바뀌기 전에는 `rds_replication_slot_lag_alarm_threshold_mb`를 높이지 않습니다.

`lost`인 경우 초기 snapshot으로 다시 발행될 Outbox 이벤트를 확인합니다.

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

## 3. Kafka Connect 관리 포트에 임시 접속

새 PowerShell 창을 열어 `infra/terraform`로 이동한 뒤 실행합니다. 이 창에서도 output 조회 전에 staging workspace를 다시 검증합니다.

```powershell
$env:AWS_PROFILE = "meetple-deploy"
$env:AWS_REGION = "ap-northeast-2"

$Workspace = terraform.exe workspace show
if ($Workspace.Trim() -ne "staging") {
  throw "staging workspace에서만 실행할 수 있습니다. 현재 workspace: $Workspace"
}

$ClusterName = terraform.exe output -raw ecs_cluster_name
$EventRuntimeService = terraform.exe output -raw event_runtime_service_name
$EcsInstancesSg = terraform.exe output -raw ecs_instance_security_group_id
$EventRuntimeSg = terraform.exe output -raw event_runtime_security_group_id

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
```

Kafka Connect의 8083 포트는 기본적으로 열려 있지 않습니다. SSM 대상 EC2 security group에서만 임시 접근을 허용합니다. `0.0.0.0/0`으로 열지 않습니다.

```powershell
aws ec2 authorize-security-group-ingress `
  --group-id $EventRuntimeSg `
  --protocol tcp `
  --port 8083 `
  --source-group $EcsInstancesSg `
  --region $env:AWS_REGION `
  --profile $env:AWS_PROFILE
```

이어서 같은 PowerShell 창에서 tunnel을 열고 복구가 끝날 때까지 유지합니다.

```powershell
aws ssm start-session `
  --target $InstanceId `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=$EventRuntimeIp,portNumber=8083,localPortNumber=18083" `
  --region $env:AWS_REGION `
  --profile $env:AWS_PROFILE
```

## 4. slot 상태에 따른 Connector 조치

다른 PowerShell 창에서 Connector 상태를 확인합니다.

```powershell
Invoke-RestMethod `
  http://localhost:18083/connectors/meetple-outbox-connector/status |
  ConvertTo-Json -Depth 10
```

### `reserved`, `extended`, `unreserved`

이 상태에서는 offset과 slot을 유지합니다. Task가 `FAILED`인 경우 실패한 task만 재시작합니다.

```powershell
Invoke-RestMethod `
  -Method Post `
  'http://localhost:18083/connectors/meetple-outbox-connector/restart?includeTasks=true&onlyFailed=true'
```

Connector와 task가 `RUNNING`으로 돌아오고 slot lag가 감소하는지 확인합니다. `wal_status`가 `lost`로 바뀐 경우에만 아래 절차로 넘어갑니다.

### `lost` 전용: Connector 중지와 offset 초기화

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

`wal_status = lost`와 Outbox 재처리 영향을 확인한 경우에만 framework-managed source offset을 초기화합니다.

```powershell
Invoke-RestMethod `
  -Method Delete `
  http://localhost:18083/connectors/meetple-outbox-connector/offsets

Invoke-RestMethod `
  http://localhost:18083/connectors/meetple-outbox-connector/offsets |
  ConvertTo-Json -Depth 10
```

`offsets`가 빈 배열인지 확인합니다.

## 5. `lost` slot 재생성과 Connector 복구

Connector가 `STOPPED`, slot이 `active = false`, `wal_status = lost`인 경우에만 RDS에서 사용할 수 없는 slot을 삭제합니다.

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

## 6. 임시 접근 제거

복구 성공 여부와 관계없이 임시 8083 ingress를 제거합니다.

```powershell
aws ec2 revoke-security-group-ingress `
  --group-id $EventRuntimeSg `
  --protocol tcp `
  --port 8083 `
  --source-group $EcsInstancesSg `
  --region $env:AWS_REGION `
  --profile $env:AWS_PROFILE
```

명령 결과의 `Return`이 `true`인지 확인하고 18083 SSM tunnel을 `Ctrl+C`로 종료합니다. RDS 확인용 15432 tunnel도 더 이상 필요하지 않으면 종료합니다.

## 재발 방지 확인

- `meetple-staging-rds-replication-slot-lag-high`가 `OK`인지 확인합니다.
- `meetple-staging-debezium-failed`가 반복되지 않는지 확인합니다.
- Dashboard의 `OldestLogicalReplicationSlotLag`와 `TransactionLogsDiskUsage` 추이를 확인합니다.
- WAL 제한을 늘리는 것만으로 장애 원인을 해결하지 않습니다. Debezium task 실패, EC2 자원 부족, RDS 연결 문제를 함께 확인합니다.
