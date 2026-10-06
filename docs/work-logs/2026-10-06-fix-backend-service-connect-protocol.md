# 작업 로그: fix/backend-service-connect-protocol

## 기본 정보

- 날짜: 2026-10-06
- 브랜치: `fix/backend-service-connect-protocol`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- AI 연동용 Backend staging 배포가 Service Connect 설정 불일치로 실패한 문제를 해결한다.

## 작업 목표

- 기존 Backend Service Connect stable config와 새 Task Definition의 port mapping을 호환시킨다.
- ECS service를 재생성하지 않고 AI 연동 환경변수와 Secret이 포함된 Revision을 배포할 수 있게 한다.

## 작업 흐름

1. 실패한 GitHub Actions의 ECS deploy 오류를 확인했다.
2. AWS에서 활성 Backend revision 35와 새 baseline revision 37의 port mapping을 비교했다.
3. 기존 stable config에는 `appProtocol`이 없고 새 revision에만 `appProtocol=http`이 추가된 것을 확인했다.
4. 변경할 수 없는 기존 stable config에 맞춰 Backend task definition의 `appProtocol`을 제거했다.
5. Terraform 검증과 실제 staging plan으로 변경 범위를 확인한다.

## 사용한 도구

- AWS CLI
- Terraform CLI
- `apply_patch`

## 실행한 주요 명령

```powershell
aws ecs describe-task-definition --task-definition meetple-staging-backend:35
aws ecs describe-task-definition --task-definition meetple-staging-backend:37
terraform fmt -check -recursive
terraform validate -no-color
terraform plan -out=backend-service-connect-protocol.tfplan
```

## 변경 파일 요약

- `infra/terraform/application_service.tf`: Backend port mapping의 변경 불가능한 `appProtocol=http` 제거
- `infra/terraform/README.md`: 기존 Service Connect TCP stable config 유지 이유 기록

## 검증

```text
terraform fmt -check -recursive: 성공
terraform validate -no-color: 성공 (기존 service discovery failure_threshold deprecation 경고 1건)
terraform plan: 성공 (1 add, 0 change, 1 destroy)
git diff --check: 성공
```

결과:

- Backend Task Definition revision만 교체하고 ECS service, RDS, VPC 등 다른 리소스는 변경하지 않는 것을 확인했다.
- 새 baseline revision에는 AI 연동 설정을 유지하면서 `appProtocol`만 제거된다.

## 이슈와 결정

- ECS Service Connect는 service 생성 후 `appProtocol` 변경을 허용하지 않는다.
- 서비스 재생성보다 기존 TCP stable config를 유지하는 편이 안전하며 HTTP 요청 전달 기능에는 영향이 없다.

## 후속 작업

- PR 병합과 Terraform apply 후 Backend staging workflow를 재실행한다.
