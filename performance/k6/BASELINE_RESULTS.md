# Meetple staging API 성능 baseline

## 테스트 환경

- 실행 일시: 2026-08-30 (Asia/Seoul)
- Git commit: `0abc7bb70e80bb061491e82bae69640e0017ed5a`
- API: `https://api.meetple.shop`
- AWS region/profile: `ap-northeast-2` / `meetple-deploy`
- 배포: ALB → ECS Backend Service, ECS EC2 Capacity Provider
- ECS Backend: task definition `meetple-staging-backend:4`, desired/running 1/1
- ECS Event Runtime: task definition `meetple-staging-event-runtime:9`, desired/running 1/1
- EC2: `t3.large` 1대에 Backend와 Event Runtime 배치
- RDS: PostgreSQL 16.13, `db.t4g.micro`, gp3 20 GiB
- 데이터: 전용 계정이 소유한 모임 1,000개, `meeting_images` 약 1,999행, 이미지 9개 재사용
- 비밀값: 이메일과 비밀번호는 PowerShell 환경변수로만 전달

## 시나리오

- Smoke: readiness, 카테고리, 로그인, 내 프로필, 모임 목록, 주최 모임 목록, 모임 상세, 토큰 재발급
- Load: 30초 ramp-up, 4분 유지, 30초 ramp-down
- Load iteration: 카테고리 → 모임 목록 → 모임 상세 → 내 프로필 → 5초 pacing
- 목록은 1,000개 데이터의 50개 페이지를 순환하고 상세는 1,000개 ID를 순환
- PostgreSQL 업무 데이터 변경 없음
- Redis에는 실행별 로그인 세션 1개 생성
- 이메일, FCM, 앱 알림, Outbox 비즈니스 이벤트 없음
- API 응답의 이미지 URL만 읽으며 S3/CloudFront 이미지 바이너리는 요청하지 않음

## k6 결과

| 단계 | 실행 시간 | 요청 수 | 평균 RPS | flow/s | 성공률 | 오류율 | p50 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Smoke 1 VU | 4.03초 | 8 | 1.98 | 0.25 | 100% | 0% | 32.69ms | 120.35ms | 125.37ms | 126.62ms |
| Load 5 VU | 5분 | 1,089 | 3.61 | 0.90 | 100% | 0% | 11.17ms | 29.63ms | 51.84ms | 137.09ms |
| Load 10 VU | 5분 | 2,177 | 7.18 | 1.79 | 100% | 0% | 10.81ms | 27.77ms | 47.01ms | 95.38ms |
| Load 30 VU | 5분 | 6,529 | 21.60 | 5.40 | 100% | 0% | 11.18ms | 32.22ms | 49.97ms | 166.04ms |
| Stress 25 RPS | 5분 | 6,765 | 22.65¹ | 22.65 | 100% | 0% | 11.18ms | 28.69ms | 45.96ms | 119.97ms |
| Stress 50 RPS | 5분 | 13,513 | 45.30¹ | 45.30 | 100% | 0% | 11.12ms | 30.77ms | 45.25ms | 131.99ms |
| Stress 75 RPS | 5분 | 20,265 | 67.69¹ | 67.69 | 100% | 0% | 11.02ms | 30.80ms | 48.16ms | 219.09ms |
| Stress 100 RPS | 5분 | 27,013 | 90.38¹ | 90.37 | 100% | 0% | 11.05ms | 31.21ms | 48.19ms | 178.87ms |
| Stress 200 RPS | 5분 | 54,013 | 180.47¹ | 180.47 | 100% | 0% | 10.65ms | 32.26ms | 51.84ms | 201.84ms |
| Stress 300 RPS | 5분 | 81,013 | 270.51¹ | 270.50 | 100% | 0% | 10.88ms | 62.66ms | 233.68ms | 612.67ms |
| Stress 400 RPS | 5분 | 108,013 | 360.61¹ | 360.61 | 100% | 0% | 10.71ms | 335.35ms | 508.80ms | 964.09ms |

¹ 30초 ramp-up과 ramp-down을 포함한 전체 평균이다. 가운데 4분 유지 구간은 각 단계의 목표 RPS로 설정했으며 모든 단계에서 `dropped_iterations`는 0건이었다.

30 VU 엔드포인트별 결과:

| 엔드포인트 | 평균 | p50 | p90 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|---:|
| 카테고리 | 8.60ms | 7.43ms | 11.99ms | 16.29ms | 25.24ms | 104.46ms |
| 모임 목록 | 21.09ms | 17.46ms | 34.36ms | 42.03ms | 64.81ms | 166.04ms |
| 모임 상세 | 13.85ms | 11.17ms | 22.39ms | 30.33ms | 47.85ms | 83.63ms |
| 내 프로필 | 13.41ms | 10.69ms | 21.78ms | 30.51ms | 44.41ms | 92.46ms |

로그인은 setup에서 한 번만 실행돼 99.75ms 단일 표본이며 로그인 성능 통계로 사용하지 않는다.

200 RPS 엔드포인트별 결과:

| 엔드포인트 | 평균 | p50 | p90 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|---:|
| 카테고리 | 8.64ms | 7.59ms | 11.21ms | 14.90ms | 23.96ms | 107.06ms |
| 모임 목록 | 16.72ms | 12.83ms | 28.95ms | 38.22ms | 59.58ms | 164.27ms |
| 모임 상세 | 14.02ms | 10.46ms | 24.87ms | 33.74ms | 52.55ms | 201.84ms |
| 내 프로필 | 14.19ms | 10.54ms | 25.41ms | 34.58ms | 53.56ms | 153.92ms |

300 RPS 엔드포인트별 결과:

| 엔드포인트 | 평균 | p50 | p90 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|---:|
| 카테고리 | 10.36ms | 7.47ms | 13.80ms | 20.74ms | 63.92ms | 488.21ms |
| 모임 목록 | 25.99ms | 12.81ms | 49.21ms | 79.27ms | 277.53ms | 578.25ms |
| 모임 상세 | 22.29ms | 10.52ms | 42.06ms | 68.28ms | 260.41ms | 576.71ms |
| 내 프로필 | 22.67ms | 10.75ms | 42.49ms | 70.32ms | 257.63ms | 612.67ms |

400 RPS 엔드포인트별 결과:

| 엔드포인트 | 평균 | p50 | p90 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|---:|
| 카테고리 | 20.40ms | 7.25ms | 34.30ms | 93.82ms | 265.30ms | 639.80ms |
| 모임 목록 | 66.54ms | 12.39ms | 275.59ms | 389.54ms | 558.87ms | 964.09ms |
| 모임 상세 | 59.92ms | 10.31ms | 248.49ms | 361.14ms | 519.33ms | 939.74ms |
| 내 프로필 | 60.25ms | 10.37ms | 251.59ms | 361.83ms | 519.61ms | 961.12ms |

## CloudWatch 결과

| 단계 | ALB 5xx | Backend CPU 평균/최대 | Backend 메모리 평균 | Event CPU 평균/최대 | Event 메모리 평균 | EC2 CPU 평균 | RDS CPU 평균/최대 | RDS 연결 최대 | RDS 최소 여유 메모리 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 5 VU | 0 | 8.05% / 12.71% | 51.43% | 28.11% / 31.34% | 44.50% | 18.68% | 4.76% / 5.38% | 14 | 162.78 MiB |
| 10 VU | 0 | 10.48% / 15.33% | 51.61% | 26.81% / 31.70% | 44.49% | 18.22% | 5.10% / 6.56% | 14 | 161.36 MiB |
| 30 VU | 0 | 13.72% / 17.94% | 51.86% | 26.83% / 31.58% | 44.49% | 18.73% | 5.51% / 6.70% | 14 | 154.86 MiB |
| 25 RPS | 0 | 11.58% / 16.48% | 51.95% | 27.66% / 32.14% | 44.50% | 18.74% | 5.44% / 6.57% | 14 | 158.43 MiB |
| 50 RPS | 0 | 22.48% / 33.39% | 52.12% | 27.50% / 32.56% | 44.59% | 20.41% | 6.78% / 8.47% | 14 | 166.18 MiB |
| 75 RPS | 0 | 29.74% / 38.34% | 52.17% | 27.69% / 32.70% | 44.59% | 24.96% | 7.91% / 9.78% | 14 | 161.90 MiB |
| 100 RPS | 0 | 38.05% / 49.62% | 52.29% | 28.37% / 33.81% | 44.59% | 28.13% | 8.88% / 10.86% | 14 | 158.89 MiB |
| 200 RPS | 0 | 60.15% / 91.38% | 52.59% | 28.38% / 32.00% | 44.62% | 31.02% | 11.80% / 16.13% | 14 | 158.52 MiB |
| 300 RPS | 0 | 86.13% / 133.75% | 54.34% | 28.50% / 33.34% | 44.64% | 41.97%² | 15.38% / 22.23% | 14 | 158.64 MiB |
| 400 RPS | 0 | 96.32% / 166.29% | 56.33% | 28.23% / 30.43% | 44.68% | 40.58%² | 18.19% / 25.42% | 14 | 155.81 MiB |

² EC2 기본 모니터링의 5분 집계 한 점으로, ECS 1분 지표와 직접 비교할 수 없다.

- 30 VU RDS ReadLatency 최대 2.20ms, WriteLatency 최대 1.58ms
- 모든 단계에서 Backend/Event Runtime RunningTaskCount 최소 1
- 테스트 후 기존 task ARN 유지, ALB target healthy, 활성 CloudWatch ALARM 0
- `OldestReplicationSlotLag`는 최대 약 64 MiB로 주기적으로 나타난 뒤 해소
- `ReplicationSlotDiskUsage`는 8,392 bytes로 일정
- `TransactionLogsDiskUsage`는 약 2.13 GiB로 일정해 WAL의 지속 누적 징후 없음
- EC2 OS 메모리는 CloudWatch Agent 미설치로 직접 측정하지 못했고 ECS cluster 메모리를 대용 지표로 사용
- 25 RPS에서 RDS ReadLatency 최대 0.83ms, WriteLatency 최대 2.00ms
- 25 RPS 종료 후 기존 Backend/Event Runtime task ARN 유지, ALB target healthy, 활성 CloudWatch ALARM 0
- 50 RPS에서 RDS ReadLatency 관측값 0ms, WriteLatency 최대 2.14ms
- 50 RPS 종료 후 기존 Backend/Event Runtime task ARN 유지, ALB target healthy, 활성 CloudWatch ALARM 0
- 75 RPS에서 RDS ReadLatency 최대 1.00ms, WriteLatency 최대 1.32ms
- 75 RPS 종료 후 기존 Backend/Event Runtime task ARN 유지, ALB target healthy, 활성 CloudWatch ALARM 0
- 100 RPS에서 RDS ReadLatency 최대 10.00ms, WriteLatency 최대 1.88ms
- 100 RPS 종료 후 기존 Backend/Event Runtime task ARN 유지, ALB target healthy, 활성 CloudWatch ALARM 0
- 200 RPS에서 RDS ReadLatency 최대 0.30ms, WriteLatency 최대 2.10ms
- 200 RPS 종료 후 기존 Backend task ARN 유지, ALB 5xx 0건, 활성 CloudWatch ALARM 0
- 300 RPS에서 RDS ReadLatency 최대 0.70ms, WriteLatency 최대 1.00ms
- 300 RPS 유지 구간에서 Backend ECS CPU가 3분 연속 평균 131% 이상이었고 ALB 분단위 p99는 최대 358.90ms
- 300 RPS 종료 후 기존 Backend task ARN 유지, ALB 5xx 0건, 활성 CloudWatch ALARM 0
- 400 RPS 유지 구간에서 Backend ECS CPU가 4분 연속 평균 161% 이상이었고 ALB 분단위 p95/p99는 최대 398.14/587.82ms
- 400 RPS에서 RDS ReadLatency 최대 2.00ms, WriteLatency 최대 1.60ms
- 400 RPS 종료 후 기존 Backend task ARN 유지, ALB 5xx 0건, 활성 CloudWatch ALARM 0

## 분석

### 발견한 병목 후보

200 RPS에서도 오류, dropped iteration, task 교체, DB 연결 증가는 없고 전체 p95 32.26ms, p99 51.84ms로 응답시간이 안정적이어서 확정된 병목은 없다. 다만 Backend CPU는 평균 60.15%, 최대 91.38%까지 상승해 다음 단계에서 가장 먼저 포화될 자원으로 확인됐다. 이는 100 RPS의 평균 38.05%, 최대 49.62%보다 큰 증가이며, 더 높은 부하에서는 응답시간이 비선형적으로 증가할 수 있다. Event Runtime CPU는 API 요청률과 관계없이 평균 약 28%를 유지해 같은 EC2의 고정 background 부하로 확인됐다. 모임 목록은 200 RPS에서 p95 38.22ms, p99 59.58ms로 네 조회 API 중 가장 느려 쿼리 분석의 우선 후보이다. RDS CPU 최대 16.13%, 연결 14개, ReadLatency 최대 0.30ms로 DB 병목 징후는 없었다.

300 RPS에서는 81,013건을 오류와 drop 없이 처리했지만 p99가 233.68ms로 경고 관찰선 200ms를 초과하고 최대 지연도 612.67ms로 증가했다. Backend ECS CPU는 유지 구간에서 3분 연속 평균 131% 이상이었으며, 이 수치는 예약 CPU 대비 사용률이므로 호스트 전체 CPU가 100%를 넘었다는 뜻은 아니지만 Backend가 예약 몫을 초과해 실행 자원을 사용했음을 나타낸다. 같은 시점의 RDS CPU 최대 22.23%, 연결 14개, ReadLatency 최대 0.70ms는 안정적이어서 현재 첫 병목 후보는 DB보다 Backend 실행 자원 또는 애플리케이션 내부 CPU·스레드 대기이다. 쿼리 병목 여부는 모임 목록 쿼리의 읽기 전용 EXPLAIN ANALYZE와 SQL 실행 횟수를 별도로 확인해야 확정할 수 있다.

400 RPS에서는 목표 요청 108,013건을 오류와 drop 없이 모두 처리하고 saturation 한계 판정선 p95 500ms, p99 1,000ms도 통과했다. 그러나 300→400 RPS에서 p95는 62.66→335.35ms, p99는 233.68→508.80ms로 비선형 증가했고 실제 활성 VU도 최대 104→166개로 늘어 대기열이 커졌다. Backend ECS CPU는 유지 구간 내내 예약량 대비 161~166%를 사용한 반면 RDS CPU 최대 25.42%, 연결 14개로 유지됐다. 따라서 400 RPS는 처리 가능한 최대치로 단정할 수 없지만, 현재 단일 Backend의 포화 구간에 진입한 것으로 판단한다.

Event Runtime CPU는 API 부하와 관계없이 평균 약 27%를 사용한다. 현재는 EC2 포화로 이어지지 않았지만 Backend와 Event Runtime이 단일 EC2 자원을 공유하므로 더 높은 부하에서는 CPU·메모리 경쟁 여부를 함께 확인해야 한다.

RDS CPU와 연결 수에는 여유가 있었지만 `db.t4g.micro`의 FreeableMemory가 30 VU에서 최소 154.86 MiB였다. 목표 RPS를 높일 때 CPU보다 여유 메모리와 latency를 먼저 감시한다.

### 잠정 threshold 제안

baseline을 근거로 다음 값을 staging 읽기 API의 잠정 기준으로 제안한다. 실제 사용자 SLO가 확정되기 전까지는 최종 기준이 아니다.

- API 성공률 99% 이상
- HTTP 오류율 1% 미만
- ALB Target 5xx 0건
- 경고 관찰선: 전체 p95 100ms, p99 200ms
- saturation 테스트 한계 판정: 전체 p95 500ms 미만, p99 1,000ms 미만
- Backend/Event Runtime task 재시작 0건
- arrival-rate 테스트의 `dropped_iterations` 0건

### 개선 전후 비교 상태

이번 실행은 성능 개선 전 baseline 수집이다. 아직 쿼리나 인프라 튜닝을 적용하지 않았으므로 개선 효과를 주장하지 않는다. 목표 RPS 테스트에서 포화 구간과 병목을 재현한 뒤, EXPLAIN ANALYZE·인덱스·쿼리 수를 읽기 전용으로 확인하고 동일 조건으로 재측정한다.

### 한계

- 각 Load 단계는 1회, 5분 실행이므로 p99 변동성을 일반화할 수 없음
- 단일 테스트 계정과 공유 access token을 사용해 다계정 인증·권한 부하는 반영하지 않음
- 사용자 행동형 Load는 5초 pacing 때문에 30 VU에서도 평균 21.60 RPS로 제한됨
- 이미지 URL만 조회해 실제 Flutter 이미지 다운로드와 CloudFront/S3 성능은 제외됨
- 쓰기 API, 로그인 집중 부하, 채팅, Kafka Consumer 처리량은 제외됨
- EC2 기본 모니터링의 낮은 시간 해상도와 OS 메모리 부재로 순간 자원 경합을 완전히 관찰할 수 없음

## 다음 단계: 목표 RPS Stress

`ramping-arrival-rate`로 카테고리, 목록, 상세, 프로필에 요청을 25%씩 분배한다. 각 단계는 별도 승인과 CloudWatch/Slack 확인 후 실행한다.

| 단계 | 목표 처리량 | 실행 시간 | 예상 읽기 요청 수 | 상태 |
|---|---:|---:|---:|---|
| 1 | 25 RPS | 5분 | 약 6,750건 | 통과: 6,765건, 오류·drop 0 |
| 2 | 50 RPS | 5분 | 약 13,500건 | 통과: 13,513건, 오류·drop 0 |
| 3 | 75 RPS | 5분 | 약 20,250건 | 통과: 20,265건, 오류·drop 0 |
| 4 | 100 RPS | 5분 | 약 27,000건 | 통과: 27,013건, 오류·drop 0 |
| 5 | 200 RPS | 5분 | 약 54,000건 | 통과: 54,013건, 오류·drop 0 |
| 6 | 300 RPS | 5분 | 약 81,000건 | 처리 완료: 81,013건, 오류·drop 0, p99 경고선 초과 |
| 7 | 400 RPS | 5분 | 약 108,000건 | 통과: 108,013건, 오류·drop 0, 포화 구간 진입 |

오류율 급증, ALB 5xx, task 교체, RDS 과부하 또는 Slack 경보가 발생하면 즉시 중단한다. 75 RPS 이후 증가는 이 결과를 확인한 뒤 별도 범위로 결정한다.
