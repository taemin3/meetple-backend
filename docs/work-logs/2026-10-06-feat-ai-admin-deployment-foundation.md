# 작업 로그: feat/ai-admin-deployment-foundation

## 기본 정보

- 날짜: 2026-10-06
- 브랜치: `feat/ai-admin-deployment-foundation`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- 기존 AWS/Terraform 구성에 AI 서비스와 Admin 웹을 실제 배포할 수 있는 1단계 기반을 추가한다.
- 실제 AWS 리소스 적용은 하지 않고 코드, 문서, 정적 검증과 브랜치 push까지만 수행한다.

## 작업 목표

- AI 전용 ECR, private ECS service, Service Connect, secret/log/monitoring 기반을 만든다.
- Admin SPA를 private S3와 CloudFront OAC로 배포할 기반을 만든다.
- Backend, AI, Admin repository가 장기 access key 없이 배포할 수 있도록 GitHub OIDC role을 분리한다.

## 작업 흐름

1. 기존 ECS EC2, bridge mode, Cloud Map, ECR, OIDC 구성을 확인했다.
2. AI ECS service와 Backend 양쪽에 Service Connect endpoint를 연결했다.
3. Admin S3/CloudFront 및 `/api/*` no-cache origin을 구성했다.
4. repository별 least-privilege GitHub OIDC role, output, 예시 변수와 운영 문서를 추가했다.
5. Terraform 정적 검증과 backend test를 실행했다.

## 사용한 도구

- `shell_command`
- `apply_patch`
- Terraform CLI 1.10.5 (HashiCorp 공식 바이너리)
- Gradle

## 실행한 주요 명령

```powershell
terraform fmt -check -recursive
terraform validate -no-color
.\gradlew.bat test
git diff --check
```

## 변경 파일 요약

- `infra/terraform/ai_service.tf`: AI execution/task role, log group, task definition, ECS service와 Service Connect endpoint
- `infra/terraform/admin_hosting.tf`: private S3, OAC, SPA rewrite, API proxy와 CloudFront distribution
- `infra/terraform/github_actions.tf`: Backend/AI/Admin별 OIDC trust와 배포 권한
- `infra/terraform/application_service.tf`: AI private endpoint, shared token과 feature flag 주입
- `infra/terraform/ecr.tf`, `monitoring.tf`, `event_runtime_recovery.tf`: AI image, 관측성, secret rotation 대응
- `infra/terraform/variables.tf`, `outputs.tf`, `terraform.tfvars.example`, `README.md`: bootstrap 입력/output과 운영 절차

## 검증

```text
terraform fmt -check -recursive: 성공
terraform validate -no-color: 성공 (기존 service discovery failure_threshold deprecation 경고 1건)
gradlew.bat test: 실패 (577개 중 31개 실패, 26개 skip)
git diff --check: 성공
```

결과:

- Terraform 구성 문법과 AWS provider schema 검증은 통과했다.
- 전체 테스트는 로컬 `127.0.0.1:6379` Redis가 실행 중이지 않아 `MemberControllerTest`, `PushDeviceTokenControllerTest`, `SecurityConfigTest`의 Redis 연결 테스트 31개가 실패했다. Java compile과 test 실행 자체는 완료됐으며 이번 변경에는 Java 코드가 없다.
- 실제 `terraform apply`와 AWS 리소스 생성은 수행하지 않았다.

## 이슈와 결정

- 기존 backend/event-runtime과 같은 ECS cluster/capacity provider를 재사용하되 AI는 별도 task/service로 분리했다.
- AI는 public ALB에 연결하지 않고 ECS Service Connect로 Backend와 통신한다.
- Service Connect가 주입하는 proxy 자원을 확보하도록 Backend task는 CPU 768/메모리 1,664 MiB, AI task는 CPU 512/메모리 1,664 MiB의 task-level 상한을 명시했다.
- Spring Boot container CPU 512는 유지하고, 저부하 staging에서 한 `t3.large`에 배치할 수 있도록 Event Runtime 보조 container와 AI container의 CPU 예약을 줄였다. Kafka와 Kafka Connect의 메모리 예약은 유지했다.
- CloudWatch 비용을 줄이기 위해 Container Insights와 상시 애플리케이션 custom metric 발행을 끄고, 기본 AWS metric 기반 alarm 5개와 Backend/AI `ERROR`를 포함한 핵심 log alarm 5개만 유지했다. ECS log 보존 기간은 7일로 줄였다.
- Container Insights 없이 task 0개 장애를 감지하도록 실행 중 task가 없을 때 누락되는 기본 ECS CPU metric을 Backend/AI/Event Runtime별로 2분간 감시한다.
- Backend가 고객 관리형 KMS key로 암호화된 공유 AI secret을 읽을 수 있도록 조건부 `kms:Decrypt` 권한을 추가하고, Service Connect의 AI 요청 timeout을 60초로 늘렸다.
- Terraform baseline 설정을 바꾼 뒤 활성 ECS revision을 갱신하도록 최초 배포 순서에 AI와 Backend workflow 재실행 단계를 명시했다.
- Admin 인증 API 응답이 CloudFront에 남지 않도록 `/api/*` behavior는 caching disabled로 고정했다.
- AI 자동 경고는 기본 비활성화하며 AI service가 먼저 정상 기동된 뒤 별도 적용한다.

## 후속 작업

- `meetple-ai` repository에 immutable SHA ECR/ECS 배포 workflow를 추가한다.
- `meetple-admin` repository에 S3 sync/CloudFront invalidation workflow를 추가한다.
- staging plan 검토 후 bootstrap apply, 각 workflow 최초 실행, desired count 증가 순서로 배포한다.
