# AWS ECS EC2 + RDS 기반 인프라

Meetple 백엔드의 AWS 배포를 위한 Terraform 기반 인프라입니다. 기본값은 비용을 낮춘 `staging` 환경을 대상으로 하며, 이 디렉터리만 적용해도 애플리케이션이 배포되지는 않습니다.

## 이번 단계의 범위

생성 대상:

- 2개 가용 영역의 VPC
- ALB와 ECS EC2용 퍼블릭 서브넷 2개
- 외부 경로가 없는 RDS용 DB 서브넷 2개
- Application Load Balancer와 `/readyz` 대상 그룹
- ECS 클러스터, EC2 Auto Scaling Group, Capacity Provider
- 백엔드 이미지용 ECR 저장소
- PostgreSQL 16 RDS와 RDS 관리형 master secret
- CloudWatch Logs 그룹과 최소 IAM/보안 그룹

제외 대상:

- ECS task definition 및 service
- Redis, Kafka, Kafka Connect, Debezium
- 애플리케이션 secret과 S3 이미지 버킷 권한
- ECR 이미지 push 및 GitHub Actions 배포
- Route 53 레코드와 ACM 인증서 발급
- 실제 `terraform apply`

위 제외 항목이 남아 있으므로 이 단계의 ALB는 정상 애플리케이션 target이 등록되기 전까지 `503`을 반환합니다.

## 배치 구조

```text
Internet
   |
   v
ALB (public subnet x 2)
   |
   v
ECS application task (후속 단계, bridge mode + dynamic host port)
   |
   v
RDS PostgreSQL (database subnet x 2, public access 차단)

ECS cluster
   `- EC2 Auto Scaling Group (기본 1대, SSM 접속, SSH 미개방)
```

NAT Gateway의 고정 비용을 피하기 위해 ECS EC2는 퍼블릭 서브넷에 배치됩니다. 후속 ECS task는 `bridge` network mode와 동적 host port를 사용해 인스턴스의 인터넷 경로를 공유합니다. 인스턴스에는 퍼블릭 IP가 생기지만 SSH 포트는 열지 않으며, inbound는 ALB 보안 그룹에서 오는 동적 host port 범위만 허용합니다. 이 구성은 저비용 staging 절충안이지 다중 장애에 대비한 고가용성 production 구성은 아닙니다.

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
terraform validate
terraform plan -out=meetple-staging.tfplan
```

`backend.hcl`, `terraform.tfvars`, state, plan 파일은 Git에서 제외됩니다. 비밀번호나 API key를 tfvars에 넣지 않습니다.

## 적용 전 필수 확인

- `terraform plan`의 리소스 수와 월 비용을 확인합니다.
- staging이라도 ALB, EC2, EBS, RDS, RDS backup, public IPv4 비용이 발생합니다.
- `certificate_arn = null`이면 HTTP listener가 target group으로 직접 전달합니다. 실제 외부 서비스 전에는 ACM 인증서를 연결해야 합니다.
- `environment = "production"`은 HTTPS/ALB 삭제 보호와 `db_multi_az = true`, `db_deletion_protection = true`, `db_skip_final_snapshot = false`를 강제합니다.
- `db_skip_final_snapshot = false`이면 충돌하지 않는 `db_final_snapshot_identifier`를 지정합니다.
- 기본 `t3.small`은 인프라 형태를 잡기 위한 값입니다. Spring Boot와 Redis/Kafka를 어떻게 배치할지 결정한 뒤 메모리를 다시 산정합니다.
- RDS logical replication은 아직 활성화하지 않습니다. Kafka Connect/Debezium을 배치하는 후속 단계에서 WAL 보존과 replication slot 운영 기준까지 함께 적용합니다.

실제 AWS 리소스 생성은 plan을 검토한 뒤 별도 승인 단계에서만 수행합니다.
