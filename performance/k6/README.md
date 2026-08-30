# Meetple staging k6 성능 테스트

이 디렉터리는 Meetple staging API의 재현 가능한 Smoke/Load baseline과 승인 기반 Stress 결과를 수집하기 위한 코드입니다. 기본 대상은 로컬(`http://127.0.0.1:8080`)이며, 원격 URL은 명시적인 실행 승인 옵션 없이는 차단됩니다. 2026-08-30 baseline은 [BASELINE_RESULTS.md](BASELINE_RESULTS.md)에 정리되어 있습니다.

## 현재 포함된 시나리오

### Smoke

- 1 VU, 1 iteration, 최대 8개 요청
- `/readyz`
- `GET /api/v1/categories`
- `POST /api/v1/auth/login`
- `GET /api/v1/users/me`
- `GET /api/v1/meetings`
- `GET /api/v1/users/me/meetings/hosted`
- `GET /api/v1/meetings/{meetingId}`
- `POST /api/v1/auth/reissue`

로그인은 Redis refresh session을 만들고, reissue는 같은 session의 토큰을 회전합니다. PostgreSQL 업무 데이터, 이메일, FCM, 이미지, Outbox, Kafka 메시지는 변경하거나 생성하지 않습니다.

### Load

- 30초 ramp-up, 4분 유지, 30초 ramp-down
- VU별 5초 간격으로 4개 읽기 API 호출
- 로그인은 `setup()`에서 한 번만 실행
- 대상: 카테고리, 모임 목록, 전용 모임 상세, 내 정보
- 5 VU 약 1,080개 읽기 요청 + 로그인 1회
- 10 VU 약 2,160개 읽기 요청 + 로그인 1회
- 30 VU 약 6,480개 읽기 요청 + 로그인 1회

응답시간이 5초를 초과하면 실제 요청 수는 추정치보다 작아집니다. 검색/주변 검색은 baseline 결과를 확인한 뒤 별도 시나리오로 추가합니다.

### Stress

- `ramping-arrival-rate`로 VU 수가 아닌 전체 목표 RPS를 고정
- 카테고리, 모임 목록, 모임 상세, 내 프로필에 요청을 25%씩 분배
- 30초 ramp-up, 4분 유지, 30초 ramp-down
- 승인 가능한 단계: 25 → 50 → 75 → 100 → 200 → 300 → 400 RPS
- 각 단계 사이에 k6, CloudWatch, ECS task, WAL, Slack 상태 확인
- baseline 경고 관찰선: 전체 p95 100ms, p99 200ms
- saturation 테스트 한계 판정: 전체 p95 500ms 미만, p99 1,000ms 미만
- 오류, 5xx, 계약 실패는 안전 가드와 k6 threshold로 조기 중단

## 준비

### 1. k6 설치

k6가 설치되어 있지 않다면 Windows standalone ZIP 또는 MSI를 사용합니다.

- 공식 설치 문서: https://grafana.com/docs/k6/latest/set-up/install-k6/
- standalone ZIP: 사용자 디렉터리에 압축을 풀고 `k6.exe`가 있는 디렉터리를 PATH에 추가하면 보통 관리자 권한이 필요 없습니다.
- MSI: 설치 범위와 PC 정책에 따라 관리자 권한이 필요할 수 있습니다.

새 PowerShell에서 설치를 확인합니다.

```powershell
k6 version
```

### 2. Meetple staging 테스트 데이터

Meetple 앱에서 이메일 인증이 완료된 테스트 전용 회원을 준비합니다. 회원가입과 데이터 생성은 부하 시나리오 자체에는 넣지 않습니다.

비밀번호와 토큰은 파일, Git, 명령 인자에 저장하지 않습니다. 테스트를 실행할 PowerShell 세션에서만 설정합니다.

```powershell
$env:K6_EMAIL="테스트 계정 이메일"
$env:K6_PASSWORD="테스트 계정 비밀번호"
$env:K6_MEETING_ID="테스트 모임 숫자 ID"
```

Smoke는 `K6_MEETING_ID`가 없으면 로그인한 계정이 만든 최신 모임을 찾아 ID를 출력합니다. Load를 실행할 때는 출력된 전용 모임 ID를 반드시 설정합니다.

### 3. 이미지가 포함된 baseline 데이터셋

`performance/k6/data/images`의 승인된 PNG 9장을 실제 presigned URL 흐름으로 한 번씩 업로드하고, 같은 테스트 계정으로 모임을 생성합니다. 이미지 object key는 카테고리별로 재사용하며 각 모임에는 1~3장이 연결됩니다. 원본 이미지와 생성 manifest는 Git에서 제외됩니다.

- 1차 검증: 모임 100개, `meeting_images` 약 199행
- 최종 baseline: 누적 모임 1,000개, `meeting_images` 약 1,999행
- 속도: 기본 초당 최대 2개 생성
- 외부 효과: Redis 로그인 세션 1개, PostgreSQL/WAL 쓰기, S3 PUT 9회
- 제외 효과: 이메일, FCM, 앱 알림, Outbox 비즈니스 이벤트

원격 요청 없이 계획과 이미지 파일만 확인합니다.

```powershell
.\performance\k6\scripts\Invoke-SeedTestData.ps1 `
  -Count 100 `
  -BaseUrl https://api.meetple.shop `
  -DryRun
```

승인을 받은 뒤에만 첫 100개를 생성합니다.

```powershell
.\performance\k6\scripts\Invoke-SeedTestData.ps1 `
  -Count 100 `
  -DatasetId meetple-k6-baseline-v1 `
  -BaseUrl https://api.meetple.shop `
  -AllowRemote `
  -ConfirmTarget api.meetple.shop `
  -AcknowledgeDataCreation
```

CloudWatch, WAL, ECS task와 Slack이 정상임을 확인한 뒤 같은 명령의 `-Count`만 `1000`으로 바꾸면 manifest에 기록된 100개를 건너뛰고 900개를 추가합니다. 중간에 중단돼도 성공 직후 기록된 meeting ID부터 이어서 생성합니다.

데이터셋을 사용하는 Load는 단일 `K6_MEETING_ID` 대신 manifest의 활성 meeting ID와 목록 페이지를 순환합니다.

```powershell
.\performance\k6\scripts\Invoke-Load.ps1 `
  -Vus 10 `
  -DatasetId meetple-k6-baseline-v1 `
  -BaseUrl https://api.meetple.shop `
  -AllowRemote `
  -ConfirmTarget api.meetple.shop `
  -AcknowledgeLoad
```

전체 테스트가 끝난 뒤에만 별도 승인을 받아 manifest의 ID를 soft delete합니다. 실제 이미지 삭제는 30일 보존 정책 이후 purge와 Outbox/Kafka Consumer 경로에서 수행될 수 있습니다.

```powershell
.\performance\k6\scripts\Invoke-CleanupTestData.ps1 `
  -DatasetId meetple-k6-baseline-v1 `
  -BaseUrl https://api.meetple.shop `
  -DryRun
```

## 원격 호출 없는 로컬 검증

다음 명령은 스크립트를 해석할 뿐 API 요청을 보내지 않습니다.

```powershell
Set-Location C:\project\meetple\backend
k6 inspect .\performance\k6\scenarios\smoke.js
k6 inspect .\performance\k6\scenarios\load.js
k6 inspect .\performance\k6\scenarios\stress.js
```

## staging Smoke 실행

실행 전에 확인할 내용:

- 대상: `https://api.meetple.shop`
- 1 VU, 1 iteration, 최대 8요청
- PostgreSQL 업무 데이터 변경 없음
- Redis login session 생성 및 reissue 회전
- 이메일/FCM/Kafka Consumer 부작용 없음
- CloudWatch Dashboard와 Slack을 열어둠

승인 후 사용자가 다음 명령을 직접 실행합니다.

URL은 Markdown 링크가 아닌 순수한 `https://api.meetple.shop` 문자열이어야 합니다. 요청 수가 0이거나 Smoke의 8개 요청이 완료되지 않으면 실행기는 실패로 판정합니다.

```powershell
.\performance\k6\scripts\Invoke-Smoke.ps1 `
  -BaseUrl https://api.meetple.shop `
  -AllowRemote `
  -ConfirmTarget api.meetple.shop
```

원격 실행 시 AWS CLI `meetple-deploy` 프로필을 이용한 안전 가드가 같이 동작합니다. ECS 서비스 불안정, Backend task 교체, CloudWatch ALARM, ALB Target 5xx가 감지되면 k6를 중단합니다. 사용자는 터미널에서 언제든 `Ctrl+C`로 중단할 수 있습니다.

## CloudWatch 지표 저장

CloudWatch 반영을 위해 테스트 종료 후 약 2분 기다립니다. 생성된 `run-metadata.json`의 시간을 사용해 지표를 JSON으로 저장합니다.

```powershell
$resultDirectory = '.\performance\k6\results\20260829-120000-smoke'
$metadata = Get-Content "$resultDirectory\run-metadata.json" | ConvertFrom-Json
$start = ([DateTime]$metadata.startedAtUtc).AddMinutes(-5)
$end = (Get-Date).ToUniversalTime()

.\performance\k6\scripts\Capture-CloudWatch.ps1 `
  -StartTime $start `
  -EndTime $end `
  -OutputPath "$resultDirectory\cloudwatch.json"
```

수집 항목:

- ALB RequestCount, TargetResponseTime p50/p95/p99, Target 5xx
- Backend/Event Runtime CPU, Memory, RunningTaskCount
- EC2 CPU, CPUCreditBalance
- ECS cluster MemoryUtilized/MemoryReserved
- RDS CPU, CPU credit, Connections, FreeableMemory, Read/WriteLatency, FreeStorage
- RDS replication slot lag, slot disk usage, transaction log disk usage

EC2 OS 메모리는 CloudWatch Agent가 설치되어 있지 않아 현재 직접 수집할 수 없습니다. ECS cluster 메모리를 대용 지표로 저장합니다.

## Load 실행 순서

Smoke의 k6 결과, CloudWatch, Slack이 모두 정상일 때만 5 VU를 실행합니다. 각 VU 단계가 끝날 때마다 같은 검증을 반복합니다.

```powershell
.\performance\k6\scripts\Invoke-Load.ps1 `
  -Vus 5 `
  -BaseUrl https://api.meetple.shop `
  -AllowRemote `
  -ConfirmTarget api.meetple.shop `
  -AcknowledgeLoad
```

정상일 때만 `-Vus 10`, 이후 `-Vus 30`으로 실행합니다. 세 명령을 한꺼번에 붙여 실행하지 않습니다.

## Stress 실행 순서

Load 30 VU 결과와 CloudWatch, Slack이 모두 정상이고 해당 RPS 단계에 대한 별도 승인을 받은 경우에만 실행합니다. `25`, `50`, `75`, `100`, `200` 이외의 값은 실행기가 거부합니다.

```powershell
.\performance\k6\scripts\Invoke-Stress.ps1 `
  -TargetRps 25 `
  -DatasetId meetple-k6-baseline-v1 `
  -BaseUrl https://api.meetple.shop `
  -AllowRemote `
  -ConfirmTarget api.meetple.shop `
  -AcknowledgeStress
```

25 RPS 결과가 정상일 때만 같은 명령을 `-TargetRps 50`으로 실행하고, 다시 확인한 뒤 `75`, `100`으로 올립니다. 200 RPS 이상의 각 단계는 직전 결과와 별도 위험 승인을 확인한 경우에만 실행합니다. 그 외 중간값과 400 RPS 초과 단계는 현재 실행기에서 차단합니다. Stress의 한계 판정 기준은 p95 500ms, p99 1,000ms이며 초과해도 한계 관찰을 위해 실행을 중단하지 않고 최종 결과만 실패로 표시합니다. 오류·5xx·요청 누락은 즉시 중단합니다.

## 결과와 비밀값 정리

결과는 `performance/k6/results/<run-id>`에 생성되고 Git에서 제외됩니다. 다음 파일을 분석에 사용합니다.

- `run-metadata.json`: 환경, VU, 시간, 예상 요청 수, 데이터 영향
- `k6-summary.json`: 처리량, 성공률, 오류율, p50/p90/p95/p99, 엔드포인트별 응답시간
- `cloudwatch.json`: ALB/ECS/EC2/RDS 지표

실행을 마치면 현재 PowerShell 세션에서 비밀값을 제거합니다.

```powershell
Remove-Item Env:K6_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:K6_EMAIL -ErrorAction SilentlyContinue
Remove-Item Env:K6_MEETING_ID -ErrorAction SilentlyContinue
```

응답시간 threshold는 baseline을 얻기 전에는 설정하지 않습니다. 현재 threshold는 계약 오류, HTTP 실패, 인증/비즈니스 오류, 5xx가 없어야 한다는 안전 조건만 검사합니다.
