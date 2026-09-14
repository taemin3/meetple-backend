# Staging 채팅 STOMP 부하 측정

이 절차는 `https://api.meetple.shop`의 Meetple staging만 대상으로 한다. production, 실제 FCM 발송, 새 AWS 자원 생성은 범위 밖이다. 일반 k6 HTTP 시나리오 대신 기존 Node STOMP 실행기를 사용하되, 전송 일정은 open-loop 방식으로 유지하고 예정량·시도량·미전송량을 기록한다.

## 안전 경계

- 실행기는 HTTPS `api.meetple.shop`만 허용하며 `-ConfirmTarget api.meetple.shop`과 `-AcknowledgeStagingLoad`를 모두 요구한다.
- fixture는 `targetEnvironment=staging`, `fixtureKind=chat-load-v1`, `pushDeviceCount=0`인 manifest만 허용한다.
- 테스트 계정에는 FCM device를 등록하지 않는다. 실제 FCM 발송이 관측되면 즉시 중단한다.
- 한 조건은 최대 100 RPS, 60초, 6,000개 메시지로 제한한다.
- 결과와 manifest는 커밋하지 않는다. manifest에는 생성된 테스트 비밀번호가 들어 있다.
- 서버 내부 phase recorder API는 staging에서 사용하지 않는다. 저장 여부는 인증된 채팅 history API로 대조하고 ECS/RDS/Hikari/잠금 지표는 별도로 수집한다.

## 사전 준비

필요한 로컬 도구는 AWS CLI, Session Manager plugin, `psql`, Docker, Node.js 22 이상이다. 먼저 AWS 콘솔에서 대상이 `meetple-staging-cluster / meetple-staging-backend`이고 backend Task가 1개인지 확인한다. 현재 Task definition revision, image SHA, worker 환경변수와 테스트 시작 전 CloudWatch/RDS 상태를 기록한다.

터미널 1에서 staging RDS 터널을 유지한다.

```powershell
cd C:\project\meetple\backend
.\performance\k6\scripts\Open-StagingPostgresTunnel.ps1 -LocalPort 15433 -DryRun
.\performance\k6\scripts\Open-StagingPostgresTunnel.ps1 -LocalPort 15433
```

별도 터미널에서 생성 계획을 확인한 뒤 전용 fixture를 만든다. 개선 전 서버에는 `chat_room_sequences`가 없을 수 있으므로 스크립트가 테이블 존재 여부를 확인하고 조건부로 순번 행을 만든다.

```powershell
cd C:\project\meetple\backend
.\performance\New-ChatStagingFixture.ps1 `
  -DatasetId chat-staging-baseline `
  -ManifestPath C:\secure\chat-staging-baseline.json `
  -ConfirmTarget api.meetple.shop `
  -LocalPort 15433 `
  -DryRun

.\performance\New-ChatStagingFixture.ps1 `
  -DatasetId chat-staging-baseline `
  -ManifestPath C:\secure\chat-staging-baseline.json `
  -ConfirmTarget api.meetple.shop `
  -LocalPort 15433 `
  -AcknowledgeStagingDataCreation
```

생성 영향은 전용 category 최대 1개, 회원 10명, 모임 10개, 승인 참여 90건이다. 기존 데이터는 변경하지 않으며 push device, 메시지, 읽음 상태, Outbox는 fixture 생성 시 만들지 않는다. 마지막 fixture 삭제 시 참조가 없는 전용 category도 정리한다.

## 조건별 실행

터미널 2에서 조건별로 서로 다른 결과 경로를 사용해 PostgreSQL 잠금을 캡처한다. 부하가 끝나면 즉시 `Ctrl+C`로 종료한다.

```powershell
.\performance\Capture-ChatStagingPostgresLocks.ps1 `
  -LocalPort 15433 `
  -OutputPath .\performance\results\chat-staging\chat-staging-baseline-smoke-locks.jsonl `
  -IntervalSeconds 0.1
```

터미널 3에서 smoke를 실행한다.

```powershell
.\performance\Invoke-ChatStaging.ps1 `
  -Manifest C:\secure\chat-staging-baseline.json `
  -Scenario focused `
  -RunId chat-staging-baseline-smoke `
  -ConfirmTarget api.meetple.shop `
  -Smoke `
  -AcknowledgeStagingLoad
```

Smoke가 손실 없이 끝났을 때만 20 RPS를 실행한다.

```powershell
.\performance\Invoke-ChatStaging.ps1 `
  -Manifest C:\secure\chat-staging-baseline.json `
  -Scenario focused `
  -RunId chat-staging-baseline-focused-r1 `
  -ConfirmTarget api.meetple.shop `
  -Rps 20 -DurationSeconds 60 -WarmupSeconds 10 -SettleSeconds 120 `
  -AcknowledgeStagingLoad
```

`Scenario distributed`와 새 RunId로 같은 조건을 실행한다. 개선 전 baseline을 저장한 뒤 PR을 머지하고 staging 배포가 끝나면 새 fixture와 `chat-staging-after-*` RunId로 같은 20 RPS 조건을 반복한다.

## 50 RPS와 100 RPS

20 RPS의 focused/distributed가 모두 정상이고 ECS/RDS에 여유가 있을 때만 한 단계씩 올린다.

```powershell
# 50 RPS x 30초 = 1,500건
.\performance\Invoke-ChatStaging.ps1 `
  -Manifest C:\secure\chat-staging-after.json `
  -Scenario focused `
  -RunId chat-staging-after-50-focused-r1 `
  -ConfirmTarget api.meetple.shop `
  -Rps 50 -DurationSeconds 30 -WarmupSeconds 10 -SettleSeconds 120 `
  -AcknowledgeStagingLoad

# 100 RPS x 30초 = 3,000건
.\performance\Invoke-ChatStaging.ps1 `
  -Manifest C:\secure\chat-staging-after.json `
  -Scenario focused `
  -RunId chat-staging-after-100-focused-r1 `
  -ConfirmTarget api.meetple.shop `
  -Rps 100 -DurationSeconds 30 -WarmupSeconds 10 -SettleSeconds 180 `
  -AcknowledgeStagingLoad
```

각 단계에서 distributed도 별도 RunId로 실행한다. 100 RPS 1회가 정상이어도 안정 처리량으로 단정하지 않는다. 최종 채택 조건은 focused/distributed를 각각 3회 반복한다.

## Worker 비교

개선 PR 배포 후 GitHub Actions의 `Deploy staging backend`를 `main`에서 수동 실행하면서 `chat_inbound_pool_size`와 `chat_outbound_pool_size`를 선택한다. 먼저 `4/4`로 개선 효과를 확인하고, 이후 `2/2`, `8/8`을 각각 배포해 동일한 20 RPS 조건으로 비교한다. 각 배포 후 ECS service 안정화, `/livez`, `/readyz`, Task 환경변수를 확인한다.

worker를 비교하는 동안 backend Task 수, RDS, fixture 회원 수, 구독자 수, 메시지 크기와 부하 발생 PC를 바꾸지 않는다. worker 최종값을 고른 뒤에만 50/100 RPS로 상승한다.

## 관측 및 중단 기준

결과 JSON에서 scheduled, attempted, unsent, committedInHistory, receivedByAnyClient, client receive p50/p95/p99, 중복, 미수신, sequence를 확인한다. 동시에 다음을 기록한다.

- ECS backend Task CPU/메모리, 재시작 수
- RDS CPU, DatabaseConnections, FreeableMemory
- Hikari active/pending와 애플리케이션 오류 로그
- PostgreSQL lock wait와 blocking PID

STOMP 오류, unsent, history 미저장, 저장 후 미수신, 구독자 일부 미수신, 중복 수신, sequence 오류, 5xx, Task 재시작/OOM이 하나라도 발생하면 다음 단계로 올리지 않는다. ECS CPU 85% 또는 RDS CPU 80%가 지속되거나 Hikari pending이 지속되어도 중단한다.

## 정리

먼저 삭제 대상 수만 확인하고, 결과 검토가 끝난 뒤 승인 스위치를 붙인다.

```powershell
.\performance\Remove-ChatStagingRun.ps1 -RunId chat-staging-after-100-focused-r1 -LocalPort 15433
.\performance\Remove-ChatStagingRun.ps1 `
  -RunId chat-staging-after-100-focused-r1 `
  -LocalPort 15433 `
  -AcknowledgeStagingDataDeletion

.\performance\Remove-ChatStagingFixture.ps1 -DatasetId chat-staging-after -LocalPort 15433
.\performance\Remove-ChatStagingFixture.ps1 `
  -DatasetId chat-staging-after `
  -LocalPort 15433 `
  -AcknowledgeStagingDataDeletion
```

읽음 상태는 이전 실행과 섞일 수 있어 run 단위 삭제에서 제거하지 않는다. fixture 전체를 삭제할 때 모임/회원 FK cascade 범위 안에서 함께 정리된다.
