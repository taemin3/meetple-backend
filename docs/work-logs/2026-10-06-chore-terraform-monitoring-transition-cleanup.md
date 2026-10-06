# 작업 로그: chore/terraform-monitoring-transition-cleanup

## 기본 정보

- 날짜: 2026-10-06
- 브랜치: `chore/terraform-monitoring-transition-cleanup`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- 첫 AI 배포 기반 Terraform apply가 완료된 뒤 전환용으로 임시 유지한 리소스를 정리한다.

## 작업 목표

- 첫 apply에서 상태 의존성 이전을 위해 유지했던 기존 CloudWatch 알람과 IAM 정책을 제거한다.
- 후속 plan에서 의도한 전환 리소스만 삭제되는지 확인한다.

## 작업 흐름

1. `main`에 순환 참조 해소 변경이 반영되고 첫 apply가 완료된 것을 확인했다.
2. 전환용 인프라 알람 6개를 `monitoring.tf`에서 제거했다.
3. 사용하지 않는 Backend application metric 발행 IAM 정책을 제거했다.
4. Terraform 정적 검증과 실제 staging plan을 확인한다.

## 사용한 도구

- `exec_command`
- `apply_patch`
- Terraform CLI

## 실행한 주요 명령

```powershell
terraform fmt -check -recursive
terraform validate -no-color
terraform plan -out=monitoring-transition-cleanup.tfplan
git diff --check
```

## 변경 파일 요약

- `infra/terraform/monitoring.tf`: 상태 이전용 CPU·메모리·RDS 알람 6개 제거
- `infra/terraform/application_service.tf`: 사용하지 않는 application metric 발행 IAM 정책 제거

## 검증

```text
terraform fmt -check -recursive: 성공
terraform validate -no-color: 성공 (기존 service discovery failure_threshold deprecation 경고 1건)
terraform plan: 성공 (0 add, 0 change, 7 destroy)
git diff --check: 성공
```

결과:

- 실제 staging state를 조회한 plan에서 전환용 CloudWatch 알람 6개와 Backend application metric IAM 정책 1개만 삭제 대상으로 확인했다.
- ECS service, task definition, RDS, VPC, ALB 및 AI 리소스의 추가·변경·삭제는 없다.

## 이슈와 결정

- 기본 AWS/ECS task presence 알람, ALB/RDS 핵심 알람과 Backend/AI 오류 로그 알람은 유지한다.
- 제거 대상은 첫 apply의 상태 의존성 이전을 위해 한 번만 유지했던 리소스로 제한한다.

## 후속 작업

- PR 병합 후 검토한 plan을 적용해 전환 리소스 삭제를 완료한다.
