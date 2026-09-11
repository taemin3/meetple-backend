# Staging Kafka UI 접속

Kafka UI는 `event-runtime` ECS 태스크 안에서 Kafka, Kafka Connect와 함께 실행한다. ALB나 public IP에는 연결하지 않는다. ECS 인스턴스에서 Kafka UI로 향하는 보안 그룹 트래픽만 허용하고, 개발자는 AWS SSM 터널을 통해 로컬 브라우저에서 접속한다.

## 배포 설정

staging의 Git 제외 Terraform 변수 파일에 다음 값을 설정한다.

```hcl
enable_kafka_ui = true
```

Terraform plan에서 다음 변경만 확인하고 적용한다.

- `aws_ecs_task_definition.event_runtime` 새 revision
- `aws_vpc_security_group_ingress_rule.event_runtime_kafka_ui_from_ecs_instances` 생성
- `kafka_ui_private_url` output 추가

Kafka UI 컨테이너는 128 CPU unit, 256 MiB memory reservation, 512 MiB hard limit을 사용한다. Kafka UI 장애가 Kafka와 Debezium을 중단하지 않도록 non-essential container로 실행한다.

## SSM 터널 접속

AWS CLI 로그인과 Session Manager plugin 설치를 완료하고 Terraform workspace가 `staging`인지 확인한다. 스크립트는 PATH에 설치된 Terraform을 우선 사용한다. 이 저장소의 로컬 bundled Terraform이 있으면 fallback으로 사용하며, 둘 다 없으면 `-TerraformPath`로 실행 파일을 지정한다.

저장소 루트에서 다음 스크립트를 실행한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\terraform\open-kafka-ui-tunnel.ps1
```

터널 세션을 유지한 상태에서 브라우저로 아래 주소를 연다.

```text
http://localhost:18089
```

세션을 종료하면 접근도 종료된다. 다른 로컬 포트가 필요하면 `-LocalPort`를 지정한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\terraform\open-kafka-ui-tunnel.ps1 -LocalPort 28089
```

Terraform이 PATH에 없다면 실행 파일을 직접 지정한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\terraform\open-kafka-ui-tunnel.ps1 -TerraformPath C:\HashiCorp\Terraform\terraform.exe
```

## Retry와 DLQ 확인

Kafka UI의 `Topics`에서 다음 토픽을 확인한다.

- Main: `meetple.push.notification.v1`
- Retry: `meetple.push.notification.v1.retry-0`부터 `retry-3`
- DLQ: `meetple.push.notification.v1.dlq`

`Messages`에서 측정 실행의 `runId`를 검색하면 같은 이벤트가 Main, Retry, DLQ를 통과한 기록과 Kafka header의 원본 topic, partition, offset, 예외 정보를 확인할 수 있다. DLQ 재처리가 성공해도 Kafka record는 삭제되지 않으며 retention이 끝날 때까지 남는다.

Push Main Topic과 Retry Topic의 retention은 1일이고, 운영자가 장애 원인을 확인하고 재처리할 시간이 필요한 DLQ는 14일이다. Kafka UI의 `Settings`에서 Main·Retry는 `retention.ms=86400000`, DLQ는 `retention.ms=1209600000`인지 확인한다.
