# AWS ECS EC2 + RDS 애플리케이션 배포

Meetple 백엔드와 `Outbox -> Debezium -> Kafka -> Consumer` 파이프라인을 AWS에 배포하는 Terraform 구성입니다. 기본값은 비용을 낮춘 `staging` 환경이며, 실제 AWS 변경은 `terraform plan` 검토 후 별도 `apply`로 수행합니다.

## 생성 대상

- 2개 가용 영역의 VPC, public subnet 2개, 외부 경로가 없는 DB subnet 2개
- ALB와 `/readyz` target group
- ECS EC2 cluster, Auto Scaling Group, Capacity Provider
- Spring Boot ECS task/service와 ECR repository
- PostgreSQL 16 RDS와 RDS 관리형 master secret
- 단일 ECS task의 Redis, Kafka, Kafka Connect/Debezium, connector manager
- application/retry/DLQ Kafka topic과 Outbox connector 자동 등록
- 비공개 S3 image bucket과 CloudFront Origin Access Control
- Cloud Map private DNS, IAM, security group, CloudWatch Logs
- Secrets Manager의 현재 버전 변경 시 ECS task를 교체하는 EventBridge + Systems Manager Automation

Terraform이 만들지 않는 항목:

- 애플리케이션/Firebase secret 값
- ECR image build와 push
- Route 53 record와 ACM certificate 발급
- GitHub Actions 배포
- 실제 `terraform apply`

## 배치 구조

```text
Flutter/Web
    |
    v
ALB (public subnet x 2, /readyz)
    |
    v
Spring Boot ECS service (EC2 bridge mode, dynamic host port)
    |-- JDBC ----------------------> RDS PostgreSQL (private DB subnet)
    |-- Redis/Kafka ---------------> event-runtime.<environment>.internal
    |-- presigned PUT/Delete ------> private S3 image bucket
    |-- image read URL ------------> CloudFront -> S3 (OAC)
    `-- FCM/Naver/SMTP ------------> Internet

event-runtime ECS task (awsvpc, public inbound 없음)
    |-- Redis
    |-- Kafka (single KRaft broker)
    |-- topic initializer
    |-- Kafka Connect/Debezium
    `-- connector manager ----------> RDS logical replication
```

NAT Gateway 고정 비용을 피하기 위해 ECS EC2는 public subnet에 배치됩니다. EC2에는 public IPv4가 생기지만 SSH는 열지 않고 SSM으로만 접근합니다. Spring Boot는 `bridge` mode와 동적 host port를 사용하며 외부 inbound는 ALB security group에서만 허용합니다. Redis와 Kafka는 Cloud Map private DNS와 security-group 참조로만 접근합니다.

기본 `t3.large` 한 대에 Spring Boot, Kafka, Kafka Connect, Redis를 함께 두는 구성이라 저비용 staging 절충안입니다. Kafka/Redis volume은 같은 EC2에서 task가 재시작될 때는 남지만 EC2 교체나 장애 시 유실될 수 있습니다. Kafka가 단일 broker이므로 고가용성 production 구성은 아닙니다.

## secret 준비

Terraform에는 secret 값이 아니라 기존 Secrets Manager ARN 두 개만 전달합니다. `terraform.tfvars`나 Terraform state에 비밀번호와 API key를 넣지 않습니다.

`backend_application_secret_arn`은 다음 key를 가진 JSON secret이어야 합니다.

```json
{
  "JWT_SECRET": "replace-with-at-least-32-random-bytes",
  "MAIL_HOST": "smtp.example.com",
  "MAIL_USERNAME": "replace-me",
  "MAIL_PASSWORD": "replace-me",
  "EMAIL_FROM_ADDRESS": "noreply@example.com",
  "NAVER_LOCATION_CLIENT_ID": "replace-me",
  "NAVER_LOCATION_CLIENT_SECRET": "replace-me",
  "NAVER_MAPS_CLIENT_ID": "replace-me",
  "NAVER_MAPS_CLIENT_SECRET": "replace-me"
}
```

`firebase_credentials_secret_arn`은 key로 감싼 JSON이 아니라 Firebase service-account JSON 문서 전체를 secret value로 저장합니다. ECS는 이를 `FIREBASE_CREDENTIALS_JSON`으로 주입하고 애플리케이션은 파일을 만들지 않고 메모리에서 읽습니다. 로컬의 기존 `GOOGLE_APPLICATION_CREDENTIALS` 파일 방식도 그대로 사용할 수 있습니다.

두 secret이 기본 `aws/secretsmanager` key가 아닌 고객 관리형 KMS key를 사용한다면 해당 key ARN을 `backend_secret_kms_key_arns`에 추가합니다. execution role에는 지정한 key의 `kms:Decrypt`만, 그리고 Secrets Manager를 경유하는 호출만 허용됩니다. KMS key policy도 이 execution role의 사용을 허용해야 합니다.

RDS username/password는 RDS 관리형 master secret의 `username`, `password` key를 주입합니다. 현재 Debezium과 Spring Boot가 master 계정을 공유하므로 production 전에는 application/replication 전용 DB 계정과 별도 secret을 만드는 bootstrap 단계가 필요합니다.

## 초기화와 정적 검증

필수 준비:

1. Terraform 1.10 이상과 AWS CLI를 설치합니다.
2. Terraform state용 S3 bucket을 별도로 만들고 versioning과 public access block을 켭니다.
3. 위의 application/Firebase secret을 Secrets Manager에 만들고 ARN을 준비합니다.
4. HTTPS를 사용하면 `ap-northeast-2` ACM certificate ARN을 준비합니다.
5. AWS Budget 알림을 먼저 설정합니다.

```powershell
cd infra/terraform
Copy-Item backend.hcl.example backend.hcl
Copy-Item terraform.tfvars.example terraform.tfvars

terraform fmt -check -recursive
terraform init -backend-config=backend.hcl
terraform workspace select staging
if ($LASTEXITCODE -ne 0) { terraform workspace new staging }
terraform validate
terraform plan -out=meetple-staging.tfplan
```

`staging`과 `production` workspace가 리소스 이름과 state 경로를 결정합니다. `default` workspace에서는 plan이 실패합니다. `backend.hcl`, `terraform.tfvars`, state, plan 파일은 Git에서 제외됩니다.

## 최초 배포 순서

ECR repository와 사용할 image가 동시에 처음 생기므로 두 단계로 적용합니다.

1. `terraform.tfvars`에 실제 secret ARN을 넣고 `backend_image_tag="bootstrap"`, `backend_desired_count=0`으로 plan/apply합니다.
2. 생성된 ECR에 현재 commit SHA tag로 image를 push합니다.
3. `backend_image_tag`를 push한 tag로 바꾸고 `backend_desired_count=1`로 올려 다시 plan/apply합니다.

```powershell
$Region = "ap-northeast-2"
$Repository = terraform output -raw ecr_repository_url
$ImageTag = git rev-parse --short=12 HEAD
$Registry = $Repository.Split('/')[0]

aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $Registry
docker build -t "meetple-backend:$ImageTag" ../..
docker tag "meetple-backend:$ImageTag" "${Repository}:$ImageTag"
docker push "${Repository}:$ImageTag"
```

두 번째 apply가 끝나면 다음을 확인합니다.

```powershell
$Alb = terraform output -raw alb_dns_name
curl.exe "http://$Alb/livez"
curl.exe "http://$Alb/readyz"
```

HTTPS certificate를 연결했다면 `https://`로 확인합니다. `/livez`는 프로세스 생존 여부, `/readyz`는 DB와 Redis까지 요청을 받을 준비가 됐는지를 확인합니다. ECS container health check는 `/livez`, ALB target health check는 `/readyz`를 사용합니다.

## consumer와 CDC 동작

Spring Boot task는 다음 consumer를 명시적으로 켭니다.

- FCM push consumer: `PUSH_KAFKA_CONSUMER_ENABLED=true`, `PUSH_FCM_ENABLED=true`
- email delivery consumer: `EMAIL_DELIVERY_KAFKA_CONSUMER_ENABLED=true`
- S3 image deletion consumer: `IMAGE_DELETION_KAFKA_CONSUMER_ENABLED=true`

staging 기본 listener concurrency는 각 consumer당 `1`입니다. 같은 task 안의 listener 수가 늘어나는 구조라 메모리와 Kafka partition 사용량을 확인한 뒤 최대 `3`까지 조정합니다.

RDS parameter group은 logical replication과 replication slot/WAL 상한을 설정합니다. RDS가 먼저 생기고 Flyway가 `outbox_events`를 만들기 전에는 connector가 실패할 수 있지만 connector manager가 30초마다 idempotent `PUT`으로 복구를 시도합니다. connector와 source task가 모두 `RUNNING`인지 ECS log와 Kafka Connect 상태로 확인합니다.

RDS master secret의 `AWSCURRENT`가 바뀌면 event runtime과 backend service를 각각 강제 재배포합니다. application 또는 Firebase secret의 현재 버전이 바뀌면 backend만 재배포합니다. staging에서 secret을 한 번 회전해 EventBridge -> Systems Manager Automation -> ECS deployment 순서를 검증해야 합니다.

## 이미지 저장소

S3 bucket은 public access를 모두 차단하고 CloudFront OAC만 `GetObject`를 허용합니다. Spring Boot task role은 해당 bucket의 object에만 `PutObject`, `GetObject`, `DeleteObject` 권한을 가지며, 삭제 consumer는 S3 삭제 성공 후 같은 경로를 CloudFront에서도 무효화합니다. Kafka retry가 같은 삭제를 재처리해도 동일한 caller reference를 사용해 중복 invalidation을 만들지 않습니다. 정적 access key는 사용하지 않습니다.

Flutter/Android/iOS의 presigned upload에는 CORS가 필요하지 않습니다. 웹 클라이언트를 추가할 때만 다음처럼 신뢰할 origin을 설정합니다.

```hcl
image_upload_allowed_origins = ["https://app.example.com"]
```

`image_bucket_force_destroy=false`가 기본이므로 object가 남아 있으면 destroy가 실패합니다. 폐기 가능한 staging data일 때만 `true`로 바꿉니다.

## 적용 전 운영 확인

- staging이라도 ALB, EC2, EBS, RDS, RDS backup, public IPv4, CloudFront 요청/전송 비용이 발생합니다.
- `certificate_arn=null`이면 HTTP만 노출됩니다. 실제 사용자 트래픽 전에는 ACM과 HTTPS를 연결합니다.
- `production` workspace는 HTTPS/ALB 삭제 보호와 RDS Multi-AZ/삭제 보호/final snapshot을 강제합니다.
- 기본 ALB idle timeout은 WebSocket 연결을 위해 3600초입니다.
- backend rolling deployment는 `minimumHealthyPercent=100`, `maximumPercent=200`으로 기존 정상 task를 유지한 채 교체 task를 먼저 시작합니다. 한 EC2에 자원이 부족하면 Capacity Provider가 `ecs_max_size` 범위에서 두 번째 EC2를 일시적으로 추가할 수 있으므로 배포 시간의 EC2/public IPv4 비용과 배치 상태를 확인합니다.
- RDS `Pending reboot`를 확인하고 재부팅 뒤 `SHOW rds.logical_replication;` 결과가 `on`인지 확인합니다.
- Kafka/Redis local Docker volume은 EC2 교체 전에 유실 가능성을 확인합니다.
- CloudWatch에서 ECS CPU/memory, RDS `FreeStorageSpace`, replication slot lag, ALB unhealthy target, consumer retry/DLQ를 모니터링합니다.
- 실제 AWS 리소스 생성과 secret 생성/회전은 plan 검토 후 별도 승인 단계에서 수행합니다.
