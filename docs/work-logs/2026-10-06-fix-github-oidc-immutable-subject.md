# 작업 로그: fix/github-oidc-immutable-subject

## 기본 정보

- 날짜: 2026-10-06
- 브랜치: `fix/github-oidc-immutable-subject`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- AI staging GitHub Actions가 `sts:AssumeRoleWithWebIdentity` 권한 오류로 실패하는 문제를 해결한다.

## 작업 목표

- GitHub immutable OIDC subject를 IAM Role 신뢰 정책에 반영한다.
- AI가 Backend를 호출할 수 있도록 실제 AWS에서 누락된 Backend Service Connect 설정을 Terraform으로 복구한다.

## 작업 흐름

1. AWS IAM Role 신뢰 정책과 OIDC provider를 확인했다.
2. CloudTrail에서 실패 토큰의 실제 immutable subject를 확인했다.
3. GitHub API로 AI와 Admin 저장소가 immutable subject를 사용하고 Backend는 기존 subject를 사용하는 것을 확인했다.
4. Terraform repository 변수가 기존 이름 형식과 immutable ID 형식을 모두 허용하도록 수정했다.
5. staging 로컬 변수에 AI immutable repository prefix를 반영했다.
6. ECS 배포 IAM 정책이 ECS service resource 변경에 불필요하게 연동되지 않도록 결정적인 service ARN을 사용했다.

## 사용한 도구

- AWS CLI
- GitHub CLI
- Terraform CLI
- `apply_patch`

## 실행한 주요 명령

```powershell
aws iam get-role --role-name meetple-staging-github-ai-deploy
aws cloudtrail lookup-events --lookup-attributes AttributeKey=EventName,AttributeValue=AssumeRoleWithWebIdentity
gh api repos/taemin3/meetple-ai/actions/oidc/customization/sub
terraform fmt -recursive
terraform validate -no-color
terraform plan -out=github-oidc-immutable-subject.tfplan
```

## 변경 파일 요약

- `infra/terraform/variables.tf`: GitHub immutable repository subject prefix 입력 허용
- `infra/terraform/github_actions.tf`: ECS deploy policy에 결정적인 service ARN 사용
- `infra/terraform/terraform.tfvars.example`: immutable subject 설정 안내 추가
- `infra/terraform/README.md`: GitHub API 확인 및 Terraform 입력 절차 추가

## 검증

```text
terraform fmt -recursive: 성공
terraform validate -no-color: 성공 (기존 service discovery failure_threshold deprecation 경고 1건)
terraform plan: 성공 (0 add, 2 change, 0 destroy)
git diff --check: 성공
```

결과:

- AI GitHub deploy Role의 OIDC subject를 실제 immutable repository ID 형식으로 변경하는 것을 확인했다.
- 실제 AWS에서 누락된 Backend Service Connect 설정을 in-place로 복구하는 것을 확인했다.
- 리소스 생성·삭제와 ECS task definition 교체는 없다.

## 이슈와 결정

- AI 실패 토큰의 subject는 `repo:owner@owner-id/repository@repository-id:environment:staging` 형식이었다.
- OIDC 신뢰 범위를 넓히는 wildcard 대신 GitHub가 발급한 정확한 immutable 저장소 ID와 staging Environment를 사용한다.
- 실제 Backend ECS service에는 AI 통신에 필요한 Service Connect 설정이 없으므로 다음 apply에서 함께 복구해야 한다.

## 후속 작업

- 수정 PR 병합과 Terraform apply 후 AI staging workflow를 재실행한다.
