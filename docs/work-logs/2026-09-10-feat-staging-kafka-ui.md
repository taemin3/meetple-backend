# 2026-09-10 feat/staging-kafka-ui

## 사용자 요청 요약

- Staging Kafka의 Main, Retry, DLQ record를 브라우저에서 확인할 수 있도록 Kafka UI를 배포한다.
- Push Retry 측정 중 발견된 k6 실행 오류를 수정한다.

## 작업 목표와 흐름

- Kafka UI를 Event Runtime ECS task의 선택적 sidecar로 구성한다.
- Kafka UI를 public endpoint에 연결하지 않고 ECS instance를 경유하는 SSM tunnel로만 접근한다.
- 측정 API endpoint별 k6 Trend와 충분한 teardown 제한을 추가한다.

## 변경 파일 요약

- `infra/terraform/event_runtime.tf`: Kafka UI container와 Kafka/Kafka Connect 연결 설정 추가
- `infra/terraform/security.tf`: ECS instance에서 Kafka UI 8080으로 향하는 내부 ingress 추가
- `infra/terraform/variables.tf`, `outputs.tf`: 활성화 변수, image 변수, private URL output 추가
- `infra/terraform/open-kafka-ui-tunnel.ps1`: 실행 중인 Event Runtime host를 찾아 SSM tunnel을 여는 스크립트 추가
- `docs/operations/staging-kafka-ui.md`: 배포와 접속, Retry/DLQ 확인 절차 추가
- `performance/k6`: Push Retry endpoint metric과 30분 teardown 제한 추가

## 검증

- `terraform validate -no-color`: 성공, 기존 service discovery deprecation 경고만 확인
- `terraform fmt -check`: 성공
- PowerShell script parse: 성공
- `k6 inspect performance/k6/scenarios/push-retry.js`: 성공
- Staging plan: Event Runtime task definition 교체, ECS service 갱신, Kafka UI 내부 ingress 생성만 확인

## 발견한 이슈와 결정 사항

- 10건 Push Retry 측정에서 k6의 기본 teardown 제한 60초가 4배 backoff 전체 대기시간보다 짧아 timeout이 발생했다. 제한을 30분으로 늘렸다.
- Kafka UI 장애가 Kafka와 Debezium을 재시작시키지 않도록 non-essential container로 구성했다.
- Kafka UI는 ALB와 public ingress에 연결하지 않는다.
- PR 리뷰에 따라 SSM tunnel 스크립트가 PATH의 Terraform과 명시적인 `-TerraformPath`를 지원하도록 수정했다. 로컬 bundled 실행 파일은 fallback으로만 사용한다.

## 후속 작업

- PR 병합 후 검토한 Terraform plan을 적용한다.
- SSM tunnel로 Kafka UI에 접속해 Main, Retry, DLQ topic을 확인한다.
