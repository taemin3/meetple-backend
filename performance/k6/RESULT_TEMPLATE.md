# Meetple API 성능 테스트 결과

## 테스트 환경

- 실행 일시:
- Git commit:
- API URL:
- AWS region:
- Backend ECS task definition:
- Event Runtime ECS task definition:
- EC2/RDS instance class:
- 테스트 계정/데이터 조건:

## 시나리오

- 테스트 유형: Smoke / Load / Stress
- 호출 API:
- iteration pacing:
- 데이터 생성·변경:
- 외부 부작용:

## 부하와 결과

- VU와 실행 시간:
- 총 요청 수:
- 초당 처리량:
- 성공률:
- HTTP 오류율:
- 인증 실패:
- 비즈니스 오류:
- 계약 오류:
- 전체 p50/p90/p95/p99:
- 엔드포인트별 p50/p90/p95/p99:

## AWS 지표

- ALB TargetResponseTime / Target 5xx:
- Backend CPU / Memory / RunningTaskCount:
- Event Runtime CPU / Memory / RunningTaskCount:
- EC2 CPU / CPU credit / memory 대용 지표:
- RDS CPU / CPU credit / Connections / FreeableMemory:
- RDS ReadLatency / WriteLatency:
- replication slot WAL lag / disk usage:

## 분석

- 발견한 병목:
- 병목 근거:
- 개선안:
- 개선 전후 비교:
- 현재 테스트의 한계:
