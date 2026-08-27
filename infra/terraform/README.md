# AWS ECS EC2 + RDS 기반 인프라

Meetple 백엔드의 AWS 배포를 위한 Terraform 기반 인프라입니다. 기본값은 비용을 낮춘 `staging` 환경을 대상으로 하며, 이 디렉터리만 적용해도 Spring Boot 애플리케이션이 배포되지는 않습니다.

## 이번 단계의 범위

생성 대상:

- 2개 가용 영역의 VPC
- ALB와 ECS EC2용 퍼블릭 서브넷 2개
- 외부 경로가 없는 RDS용 DB 서브넷 2개
- Application Load Balancer와 `/readyz` 대상 그룹
- ECS 클러스터, EC2 Auto Scaling Group, Capacity Provider
- 백엔드 이미지용 ECR 저장소
- logical replication을 활성화한 PostgreSQL 16 RDS와 RDS 관리형 master secret
- 단일 ECS task의 Redis, Kafka, Kafka Connect/Debezium 런타임
- Kafka application/retry/DLQ topic 초기화와 Outbox connector 자동 등록
- Cloud Map 기반 private DNS와 event runtime 전용 보안 그룹
- CloudWatch Logs 그룹과 최소 IAM/보안 그룹

제외 대상:

- Spring Boot ECS task definition 및 service
- Spring Boot의 Kafka consumer와 FCM credential 주입
- 애플리케이션 secret과 S3 이미지 버킷 권한
- ECR 이미지 push 및 GitHub Actions 배포
- Route 53 레코드와 ACM 인증서 발급
- 실제 `terraform apply`

Spring Boot ECS service가 아직 없으므로 이 단계의 ALB는 정상 애플리케이션 target이 등록되기 전까지 `503`을 반환합니다. Kafka와 Debezium은 실행되지만, 실제 FCM·이미지 삭제·이메일 consumer는 다음 애플리케이션 배포 단계에서 활성화합니다.

## 배치 구조

```text
Internet
   |
   v
ALB (public subnet x 2)
   |
   v
ECS application task (후속 단계, bridge mode + dynamic host port)
   |-- Redis/Kafka --> event-runtime.<environment>.internal
   `-- RDS PostgreSQL (database subnet x 2, public access 차단)

event-runtime ECS task (awsvpc, public inbound 없음)
   |-- Redis
   |-- Kafka (single KRaft broker)
   |-- Kafka topic initializer
   |-- Kafka Connect/Debezium
   `-- Outbox connector initializer
        `-- logical replication --> RDS PostgreSQL

ECS cluster
   `- EC2 Auto Scaling Group (기본 1대, SSM 접속, SSH 미개방)
```

NAT Gateway의 고정 비용을 피하기 위해 ECS EC2는 퍼블릭 서브넷에 배치됩니다. 후속 Spring Boot task는 `bridge` network mode와 동적 host port를 사용해 인스턴스의 인터넷 경로를 공유합니다. event runtime은 task ENI에 public IP를 할당하지 않고 Cloud Map private DNS로만 노출합니다. EC2에는 퍼블릭 IP가 생기지만 SSH 포트는 열지 않으며, inbound는 ALB와 내부 서비스에 필요한 포트만 보안 그룹 간 참조로 허용합니다.

Redis, Kafka, Kafka Connect를 하나의 ECS task로 묶은 것은 기본 1대 EC2에서 ENI 수와 메모리를 아끼기 위한 선택입니다. 컨테이너 health check와 `Kafka -> topic init -> Kafka Connect -> connector init` 시작 순서는 ECS가 관리합니다. Kafka와 Redis의 Docker volume은 같은 EC2에서 task가 재시작될 때는 유지되지만, EC2 교체·장애·종료 시 함께 사라집니다. Kafka broker도 1대이므로 이 구성은 저비용 staging 절충안이며 고가용성 production 구성은 아닙니다. 복구가 필요한 production에서는 Amazon MSK/다중 broker Kafka와 ElastiCache 또는 별도 영속화 전략을 사용해야 합니다.

## CDC 동작과 운영 주의점

- RDS custom parameter group이 `rds.logical_replication=1`과 replication slot/WAL 상한을 설정합니다. 기존 RDS에 처음 연결할 때는 parameter group 변경 후 재부팅이 필요할 수 있습니다.
- Debezium은 `public.outbox_events`만 읽고 Outbox Event Router로 행의 `topic` 값에 해당하는 Kafka topic으로 전달합니다.
- connector 등록은 ECS task 시작 때 idempotent `PUT` 요청으로 수행합니다. Kafka Connect의 internal topic과 connector 상태가 사라져도 다시 등록됩니다.
- `max_slot_wal_keep_size` 기본값은 slot 하나당 2048 MiB입니다. Debezium 장애가 길어져 이 한도를 넘으면 slot이 무효화될 수 있으므로 RDS `FreeStorageSpace`, replication slot lag, connector 상태를 함께 모니터링해야 합니다.
- 현재 Debezium은 기능 연결을 위해 RDS 관리형 master secret을 ECS execution role로 주입받습니다. 외부 공개는 되지 않지만 권한 범위가 넓으므로 production 전에는 전용 replication 계정과 별도 secret을 만드는 DB bootstrap 단계가 필요합니다.

## 사전 준비

1. Terraform `1.10` 이상을 설치합니다.
2. AWS 자격증명을 준비합니다.
3. Terraform state용 S3 버킷을 별도로 만들고 버전 관리와 퍼블릭 액세스 차단을 활성화합니다.
4. HTTPS를 바로 사용할 경우 `ap-northeast-2`의 ACM 인증서 ARN을 준비합니다.
5. AWS Billing에서 Budget 알림을 먼저 설정합니다.

Terraform은 state 버킷 자체를 생성하지 않습니다. 같은 구성에서 자신이 사용하는 backend를 만들 수 없기 때문입니다.

## 초기화와 검증

```powershell
cd infra/terraform
Copy-Item backend.hcl.example backend.hcl
Copy-Item terraform.tfvars.example terraform.tfvars

terraform fmt -check -recursive
terraform init -backend-config=backend.hcl
# 기존 staging workspace를 선택하고, 없으면 새로 생성
terraform workspace select staging
if ($LASTEXITCODE -ne 0) { terraform workspace new staging }
terraform validate
terraform plan -out=meetple-staging.tfplan
```

`staging`과 `production`은 Terraform workspace가 리소스 이름과 state 경로를 동시에 결정합니다. `default` workspace에서는 plan이 실패합니다. S3 state는 각각 `meetple/staging/terraform.tfstate`, `meetple/production/terraform.tfstate`에 저장되므로 tfvars만 바꿔 다른 환경의 state를 덮어쓸 수 없습니다.

`backend.hcl`, `terraform.tfvars`, state, plan 파일은 Git에서 제외됩니다. 비밀번호나 API key를 tfvars에 넣지 않습니다. production 작업 전에는 `terraform workspace select production`으로 workspace를 명시적으로 전환하고 `terraform workspace show`로 다시 확인합니다.

## 적용 전 필수 확인

- `terraform plan`의 리소스 수와 월 비용을 확인합니다.
- staging이라도 ALB, EC2, EBS, RDS, RDS backup, public IPv4 비용이 발생합니다.
- `certificate_arn = null`이면 HTTP listener가 target group으로 직접 전달합니다. 실제 외부 서비스 전에는 ACM 인증서를 연결해야 합니다.
- `production` workspace는 HTTPS/ALB 삭제 보호와 `db_multi_az = true`, `db_deletion_protection = true`, `db_skip_final_snapshot = false`를 강제합니다.
- `db_skip_final_snapshot = false`이면 충돌하지 않는 `db_final_snapshot_identifier`를 지정합니다.
- 기본 `t3.large`(2 vCPU, 8 GiB)는 단일 Kafka broker, Kafka Connect, Redis와 후속 Spring Boot task를 한 EC2에 올리기 위한 staging 시작값입니다. CloudWatch 메모리와 CPU를 확인한 뒤 조정합니다.
- Kafka와 Redis 데이터는 EC2 로컬 Docker volume에 저장됩니다. Launch Template 변경에 따른 instance refresh나 EC2 장애 전에 데이터 유실 가능성을 확인합니다.
- Launch Template 버전이 변경되면 ASG instance refresh가 새 EC2를 먼저 준비한 뒤 기존 인스턴스를 교체합니다.
- bridge task가 EC2 instance profile을 가져가지 못하도록 IMDSv2 응답 hop limit을 `1`로 제한합니다. 애플리케이션의 AWS 권한은 후속 task role로만 부여합니다.
- RDS parameter group의 `Apply type`과 `Pending reboot` 상태를 확인하고, 계획된 시간에 재부팅한 뒤 `SHOW rds.logical_replication;` 결과가 `on`인지 확인합니다.
- event runtime이 안정화되면 ECS task 로그에서 topic initializer와 connector initializer의 성공 종료를 확인하고 Kafka Connect connector 상태가 `RUNNING`인지 확인합니다.

실제 AWS 리소스 생성은 plan을 검토한 뒤 별도 승인 단계에서만 수행합니다.
