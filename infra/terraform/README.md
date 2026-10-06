# AWS ECS EC2 + RDS 애플리케이션 배포

Meetple 백엔드, AI 신고 분석 서비스, Admin SPA와 `Outbox -> Debezium -> Kafka -> Consumer` 파이프라인을 AWS에 배포하는 Terraform 구성입니다. 기본값은 비용을 낮춘 `staging` 환경이며, 실제 AWS 변경은 `terraform plan` 검토 후 별도 `apply`로 수행합니다.

## 생성 대상

- 2개 가용 영역의 VPC, public subnet 2개, 외부 경로가 없는 DB subnet 2개
- ALB와 `/readyz` target group
- ECS EC2 cluster, Auto Scaling Group, Capacity Provider
- Spring Boot ECS task/service와 ECR repository
- FastAPI AI ECS task/service, 별도 ECR repository와 ECS Service Connect private endpoint
- Admin SPA용 private S3 bucket, CloudFront OAC와 `/api/*` HTTPS origin
- PostgreSQL 16 RDS와 RDS 관리형 master secret
- 단일 ECS task의 Redis, Kafka, Kafka Connect/Debezium, connector manager
- application/retry/DLQ Kafka topic과 Outbox connector 자동 등록
- 비공개 S3 image bucket과 CloudFront Origin Access Control
- Cloud Map private DNS, IAM, security group, CloudWatch Logs
- Secrets Manager의 현재 버전 변경 시 ECS task를 교체하는 EventBridge + Systems Manager Automation

Terraform이 만들지 않는 항목:

- 애플리케이션/Firebase/AI secret 값
- ECR image build와 push
- Route 53 record와 ACM certificate 발급
- GitHub Environment와 repository variable
- 실제 `terraform apply`

## 배치 구조

```text
Flutter
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
    |-- Service Connect -----------> AI FastAPI ECS service (private, ai:8001)
    `-- FCM/Naver/SMTP ------------> Internet

AI FastAPI ECS service (EC2 bridge mode, public endpoint 없음)
    |-- Service Connect -----------> Spring Boot (private, backend:8080)
    |-- Kafka ---------------------> event-runtime.<environment>.internal:9092
    `-- OpenAI API ----------------> Internet

Admin browser
    `-- CloudFront
        |-- /* --------------------> private S3 admin bucket (OAC)
        `-- /api/* ----------------> HTTPS backend API origin (no cache)

event-runtime ECS task (awsvpc, public inbound 없음)
    |-- Redis
    |-- Kafka (single KRaft broker)
    |-- topic initializer
    |-- Kafka Connect/Debezium
    `-- connector manager ----------> RDS logical replication
```

NAT Gateway 고정 비용을 피하기 위해 ECS EC2는 public subnet에 배치됩니다. EC2에는 public IPv4가 생기지만 SSH는 열지 않고 SSM으로만 접근합니다. Spring Boot와 AI는 `bridge` mode와 동적 host port를 사용합니다. Spring Boot의 외부 inbound는 ALB security group에서만 허용하고, 두 서비스 간 호출은 기존 private namespace의 ECS Service Connect 이름 `backend:8080`, `ai:8001`을 사용합니다. AI에는 public load balancer를 연결하지 않습니다. Redis와 Kafka는 Cloud Map private DNS와 security-group 참조로만 접근합니다.

기본 `t3.large` 한 대에 Spring Boot, Kafka, Kafka Connect, Redis를 함께 두는 구성이라 저비용 staging 절충안입니다. Kafka/Redis volume은 같은 EC2에서 task가 재시작될 때는 남지만 EC2 교체나 장애 시 유실될 수 있습니다. Kafka가 단일 broker이므로 고가용성 production 구성은 아닙니다.

AI를 포함한 staging 기본 CPU 예약은 Event Runtime 736, Backend task 768, AI task 512로 총 2,016 CPU unit입니다. Backend는 기존 Spring Boot 512 CPU를 유지하고 Service Connect proxy를 위해 task 전체를 768로 설정합니다. AI는 애플리케이션 256과 proxy 여유 256을 합쳐 task 전체 512를 사용합니다. Event Runtime은 Kafka 384, Redis 64, Kafka Connect 256, connector manager 32로 조정했으며 일회성 Kafka init에는 CPU를 예약하지 않습니다. Kafka UI를 켜면 한 `t3.large`에 들어가지 않을 수 있습니다.

메모리는 Event Runtime 예약 3,264 MiB, Backend task 1,664 MiB, AI task 1,664 MiB로 총 6,592 MiB입니다. Backend와 AI의 1,536 MiB container limit은 유지하고 각 task의 나머지 128 MiB를 Service Connect proxy에 남깁니다. 이 값은 저부하 staging 기준이며 CloudWatch CPU/memory, Kafka consumer lag, Debezium catch-up 시간을 확인해 부족하면 Capacity Provider가 `ecs_max_size=2`까지 확장하도록 둡니다. rolling deployment 중에는 교체 task 때문에 두 번째 EC2가 일시적으로 필요할 수 있습니다.

## secret 준비

Terraform에는 secret 값이 아니라 기존 Secrets Manager ARN만 전달합니다. `terraform.tfvars`나 Terraform state에 비밀번호와 API key를 넣지 않습니다.

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

AI 검색을 활성화할 때 위 backend application secret에 `AI_SEARCH_CAPABILITY_SECRET` key도 추가합니다. 이 값은 브라우저에 노출하지 않으며 Spring Boot가 AI 검색 요청에 capability token을 만들 때만 사용합니다.

`ai_application_secret_arn`은 다음 key를 가진 별도 JSON secret이어야 합니다. 같은 `AI_SERVICE_TOKEN`을 AI와 Spring Boot 양쪽에 주입하므로 양방향 내부 API 인증 값이 일치합니다.

```json
{
  "AI_SERVICE_TOKEN": "replace-with-at-least-32-random-bytes",
  "AI_OPENAI_API_KEY": "replace-me"
}
```

AI secret이 고객 관리형 KMS key를 사용한다면 `ai_secret_kms_key_arns`에 key ARN을 추가합니다. secret의 `AWSCURRENT`가 변경되면 EventBridge가 AI와 Spring Boot service를 모두 강제 재배포해 새 값을 읽게 합니다.

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

ECR repository, GitHub 배포 role, 실제 image가 동시에 처음 생기므로 서비스별로 `bootstrap -> image 배포 -> desired_count 증가` 순서를 지킵니다. placeholder image가 없는 상태에서 task를 먼저 시작하지 않습니다.

1. `terraform.tfvars`에 실제 secret ARN을 넣고 backend/AI image tag는 `bootstrap`, 두 `desired_count`는 `0`, `ai_integration_enabled=false`로 둡니다. Admin까지 준비한다면 `admin_hosting_enabled=true`, `admin_api_origin_domain_name`도 설정합니다.
2. 필요한 `github_actions_*_deploy_enabled=true`를 켜 plan/apply하고 Terraform output의 배포 role ARN 및 배포 대상을 각 GitHub `staging` Environment에 등록합니다.
3. backend와 AI의 staging workflow를 각각 한 번 수동 실행해 commit SHA image와 실제 task definition revision을 활성화합니다. Admin workflow는 build 결과를 S3에 동기화하고 CloudFront를 무효화합니다.
4. backend/AI workflow가 성공하면 `backend_desired_count=1`, `ai_desired_count=1`, 실제 `ai_openai_model`을 적용합니다. 두 서비스의 health와 AI Kafka consumer를 확인한 뒤 마지막으로 `ai_integration_enabled=true`를 적용합니다.

`moderation_auto_warning_enabled`는 AI 연동 확인과 관리자 검토 흐름 검증 후 별도로 켭니다. AI 분석 전체를 자동 제재하는 옵션이 아니라 현재 백엔드의 제한된 경고 규칙만 활성화합니다.

서비스 기동 apply가 끝나면 다음을 확인합니다.

```powershell
$Alb = terraform output -raw alb_dns_name
curl.exe "http://$Alb/livez"
curl.exe "http://$Alb/readyz"
```

HTTPS certificate를 연결했다면 `https://`로 확인합니다. `/livez`는 프로세스 생존 여부, `/readyz`는 DB와 Redis까지 요청을 받을 준비가 됐는지를 확인합니다. ECS container health check는 `/livez`, ALB target health check는 `/readyz`를 사용합니다.

AI는 public endpoint가 없으므로 ECS task 상태, `/ecs/<prefix>/ai` 로그와 Service Connect discovery를 확인합니다. Admin은 `admin_cloudfront_domain_name` output으로 접속하고 새로고침 시 SPA rewrite, 로그인 API가 `/api/*` origin으로 전달되는지 확인합니다.

## GitHub Actions staging 배포

`.github/workflows/deploy-staging.yml`은 PR에서 테스트를 실행하고, 승인된 staging workflow가 다음 순서로 Spring Boot를 배포합니다.

이 단계에서는 AI/Admin이 사용할 AWS 리소스와 least-privilege OIDC role까지만 준비합니다. `meetple-ai`, `meetple-admin` repository의 실제 배포 workflow는 각 repository에서 별도 PR로 추가합니다.

1. Java 21로 Gradle test 실행
2. GitHub OIDC로 단기 AWS 자격 증명 발급
3. Git commit SHA를 immutable ECR tag로 build/push
4. 현재 ECS task definition에서 환경·secret·CPU·memory 설정을 가져와 image만 교체
5. 새 task definition revision을 등록하고 ECS rolling deployment 대기
6. `https://api.meetple.shop/livez`, `/readyz` smoke test

Terraform은 backend task definition의 기반 설정과 ECS service 구성을 관리합니다. GitHub Actions는 image-specific task definition revision과 ECS service의 활성 revision을 관리하므로 `aws_ecs_service.backend.task_definition`은 Terraform drift 대상에서 제외합니다. CPU, memory, environment, secret 같은 기반 설정을 Terraform에서 바꿨다면 먼저 Terraform을 적용한 뒤 staging workflow를 수동 실행해 최신 기반 revision에 애플리케이션 image를 반영합니다.

### 1. AWS OIDC role bootstrap

staging의 로컬 `terraform.tfvars`에 다음 값을 추가합니다.

```hcl
github_actions_deploy_enabled = true
github_actions_repository     = "taemin3/meetple-backend"
github_actions_ai_deploy_enabled    = true
github_actions_ai_repository        = "taemin3/meetple-ai"
admin_hosting_enabled               = true
github_actions_admin_deploy_enabled = true
github_actions_admin_repository     = "taemin3/meetple-admin"
```

AWS 계정에 `token.actions.githubusercontent.com` OIDC provider가 이미 다른 Terraform state로 관리되고 있다면 중복 생성하지 않고 해당 ARN을 전달합니다.

```hcl
github_actions_oidc_provider_arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
```

그다음 기존 절차대로 staging workspace에서 plan을 검토하고 한 번 적용합니다.

```powershell
terraform plan -out=meetple-staging-github-oidc.tfplan
terraform apply meetple-staging-github-oidc.tfplan
terraform output -raw github_actions_deploy_role_arn
terraform output -raw github_actions_ai_deploy_role_arn
terraform output -raw github_actions_admin_deploy_role_arn
```

OIDC trust는 `staging` GitHub Environment로 한정됩니다. OIDC provider는 AWS 계정 전체에서 하나만 생성해야 하므로 다른 workspace나 Terraform state에서 재사용할 때는 output ARN을 `github_actions_oidc_provider_arn`에 전달합니다.

### 2. GitHub 설정

각 GitHub repository의 `Settings -> Environments`에서 `staging` Environment를 만들고 deployment branch를 `main`으로 제한합니다. repository별 role ARN을 같은 이름의 Environment variable로 추가합니다.

```text
AWS_DEPLOY_ROLE_ARN=<terraform output github_actions_deploy_role_arn>
```

AI repository에는 `github_actions_ai_deploy_role_arn`, Admin repository에는 `github_actions_admin_deploy_role_arn` 값을 사용합니다. AI에는 ECS cluster/service/task family/ECR output을, Admin에는 bucket/distribution output을 다음 repository 작업의 배포 workflow 변수로 연결합니다.

최초 배포 순서의 수동 workflow와 `backend_desired_count=1` 적용을 완료한 뒤 health endpoint를 확인합니다. 검증이 끝나면 repository variable을 추가해 이후 `main` 애플리케이션 변경을 자동 배포합니다.

```text
AUTO_DEPLOY_ENABLED=true
```

배포 role은 repository별로 분리됩니다. Backend/AI role은 자신의 ECR push, ECS service update, task definition register, 자신의 task role `iam:PassRole`만 허용합니다. Admin role은 자신의 S3 object 동기화와 CloudFront invalidation만 허용합니다. 장기 AWS access key를 GitHub Secret에 저장하지 않습니다. 동일 commit을 재실행하면 immutable ECR tag를 재사용하도록 각 workflow를 구성합니다.

### 3. 배포 실패와 rollback

GitHub Actions는 ECS service가 안정화될 때까지 대기합니다. 새 task가 container health check 또는 ALB `/readyz`를 통과하지 못하면 ECS deployment circuit breaker가 마지막 정상 revision으로 rollback하고 workflow가 실패합니다. 현재 rolling deployment는 기존 task를 유지한 채 새 task를 시작하므로 단일 EC2 자원이 부족하면 Capacity Provider가 `ecs_max_size` 범위에서 임시 EC2를 추가할 수 있습니다.

## consumer와 CDC 동작

Staging Kafka UI는 public endpoint 없이 Event Runtime 내부에서 선택적으로 실행할 수 있습니다. `enable_kafka_ui=true`를 적용한 뒤 SSM 터널로 접속하는 절차는 [Staging Kafka UI 접속](../../docs/operations/staging-kafka-ui.md)을 따릅니다.

Spring Boot task는 다음 consumer를 명시적으로 켭니다.

- FCM push consumer: `PUSH_KAFKA_CONSUMER_ENABLED=true`, `PUSH_FCM_ENABLED=true`
- email delivery consumer: `EMAIL_DELIVERY_KAFKA_CONSUMER_ENABLED=true`
- S3 image deletion consumer: `IMAGE_DELETION_KAFKA_CONSUMER_ENABLED=true`

staging 기본 listener concurrency는 각 consumer당 `1`입니다. 같은 task 안의 listener 수가 늘어나는 구조라 메모리와 Kafka partition 사용량을 확인한 뒤 최대 `3`까지 조정합니다.

RDS parameter group은 logical replication과 replication slot/WAL 상한을 설정합니다. RDS가 먼저 생기고 Flyway가 `outbox_events`를 만들기 전에는 connector가 실패할 수 있지만 connector manager가 30초마다 idempotent `PUT`으로 복구를 시도합니다. connector와 source task가 모두 `RUNNING`인지 ECS log와 Kafka Connect 상태로 확인합니다.

RDS master secret의 `AWSCURRENT`가 바뀌면 event runtime과 backend service를 각각 강제 재배포합니다. application 또는 Firebase secret의 현재 버전이 바뀌면 backend만, AI secret이 바뀌면 AI와 backend를 재배포합니다. staging에서 secret을 한 번 회전해 EventBridge -> Systems Manager Automation -> ECS deployment 순서를 검증해야 합니다.

## Admin 정적 호스팅

Admin build 결과는 public website bucket이 아니라 public access를 차단한 S3 bucket에 저장하며 CloudFront OAC만 읽을 수 있습니다. 확장자가 없는 경로는 CloudFront Function이 `/index.html`로 rewrite해 React Router 새로고침을 처리합니다. `/api/*`는 `admin_api_origin_domain_name`의 HTTPS origin으로 전달하고 AWS managed `CachingDisabled`, `AllViewerExceptHostHeader` policy를 사용해 인증 응답을 캐시하지 않으면서 `Authorization`, cookie, query string을 전달합니다.

custom admin domain을 쓰면 `admin_domain_names`와 **us-east-1**에서 발급한 `admin_certificate_arn`을 함께 설정합니다. Route 53 record와 certificate 발급/검증은 이 구성에서 만들지 않습니다. Admin frontend에는 AWS credential이나 AI/OpenAI secret을 넣지 않습니다.

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

## CloudWatch 모니터링

`monitoring.tf`는 별도 모니터링 서버 없이 기존 ECS Container Insights와 CloudWatch Logs를 사용해 다음 항목을 구성합니다.

- `meetple-<environment>-operations` Dashboard
- ECS backend/AI/event-runtime의 CPU, memory, 실행 task 수
- ECS Auto Scaling Group의 실행 중 EC2 instance 수
- ALB unhealthy target, target HTTP 5xx, p95 응답 시간
- RDS CPU, connection, freeable memory, free storage
- PostgreSQL oldest logical replication slot lag, transaction log disk usage
- 별도로 설정한 안전 기준에 도달한 replication slot 사전 경고
- Backend/AI `ERROR`, AI moderation consumer restart, Kafka consumer `moved to DLQ`, Debezium connector failed-task 로그 지표와 알람
- ALARM과 복구(OK)를 전달하는 SNS topic

이메일 알림이 필요하면 로컬 `terraform.tfvars`에 주소를 추가합니다. 주소는 Git에 커밋하지 않습니다.

```hcl
monitoring_notification_email = "replace-with-alert-email@example.com"
```

`terraform apply` 후 AWS SNS가 보낸 `Subscription Confirmation` 메일에서 구독을 승인해야 알림이 전달됩니다.

비용 절감을 위해 staging 서버를 의도적으로 중지하기 전에는 알람 리소스를 삭제하지 않고 action만 비활성화합니다.

```hcl
monitoring_alarm_actions_enabled = false
```

이 상태로 `terraform apply`하면 Dashboard와 알람 이력은 유지되지만 SNS의 ALARM/OK 메일은 전송하지 않습니다. 서버를 다시 실행할 때 값을 `true`로 되돌려 적용합니다.

replication slot 지연 경고 기준은 `rds_replication_slot_lag_alarm_threshold_mb`로 별도 관리합니다. 기본 2,048 MiB 제한에서는 1,536 MiB입니다. `max_slot_wal_keep_size` 변경은 `pending-reboot`이므로 제한을 높이더라도 RDS 재부팅과 `SHOW max_slot_wal_keep_size;` 확인 전에는 알람 기준을 높이지 않습니다. 논리 slot용 `OldestLogicalReplicationSlotLag`가 1분 간격으로 두 번 연속 기준을 넘으면 SNS로 ALARM을 보내며, `TransactionLogsDiskUsage`는 같은 Dashboard에서 원인 판단용으로 확인합니다. `Unable to obtain valid replication slot` 로그는 즉시, source task의 `RESTARTING`이 5분 이상 지속되면 별도 알람으로 감지합니다. 알람이 발생하면 ECS만 반복 재시작하지 말고 [Debezium replication slot 복구 절차](../../docs/operations/debezium-replication-slot-recovery.md)에 따라 slot 상태와 Outbox 재처리 범위를 먼저 확인합니다.

저활동 RDS에서도 slot LSN이 주기적으로 전진하도록 V13 migration이 `debezium_heartbeat` 1행 테이블을 만들고, connector가 60초마다 해당 행을 갱신합니다. heartbeat 테이블과 native heartbeat record는 Outbox SMT predicate로 사용자 이벤트와 분리되며 전용 Kafka topic 두 개에 1일만 보관합니다. Event Runtime 시작 시에는 기존 topic 목록을 한 번만 읽고 누락 topic만 생성하며, Kafka CLI용 `kafka-init` 컨테이너는 512 MiB 상한과 256 MiB heap을 사용합니다.

`db.t4g.micro` staging의 24시간 관측값에서 `FreeableMemory`는 약 150~189 MiB, `SwapUsage`는 최대 약 12 MiB였습니다. `rds_freeable_memory_alarm_threshold_mb` 기본값은 이 baseline 아래인 128 MiB이며, DB instance class를 바꾸면 새 baseline을 측정한 뒤 함께 조정합니다. Dashboard에서는 `FreeableMemory`와 `SwapUsage`를 같이 확인합니다.
