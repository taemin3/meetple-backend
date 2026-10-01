# 작업 로그: feat/meeting-embedding-sync

## 기본 정보

- 날짜: 2026-10-01
- 브랜치: `feat/meeting-embedding-sync`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- pgvector 하이브리드 검색 병합 후 다음 단계인 모임 생성·수정 임베딩 갱신 구현

## 작업 목표

- 모임 API 응답 시간을 OpenAI 호출에 묶지 않고 임베딩을 비동기로 갱신한다.
- 수정 전 이벤트가 늦게 도착해 최신 임베딩을 덮는 일을 막는다.

## 작업 흐름

1. 모임 생성·의미 필드 수정 트랜잭션에 Outbox 이벤트를 기록한다.
2. Debezium/Kafka consumer가 AI 서버에서 1536차원 임베딩을 생성한다.
3. 이벤트 데이터와 현재 모임이 일치할 때만 `meeting_embeddings`에 upsert한다.

## 사용한 도구

- PowerShell
- Gradle
- Ruff, pytest
- Testcontainers PostgreSQL/pgvector

## 실행한 주요 명령

```powershell
.\gradlew.bat compileJava compileTestJava
.\gradlew.bat test --tests "com.meetple.backend.domain.ai.*" --tests "com.meetple.backend.domain.meeting.service.MeetingServiceTest"
```

## 변경 파일 요약

- 임베딩 Outbox publisher, Kafka consumer, AI HTTP client, 조건부 pgvector store 추가
- 모임 생성·수정 흐름에 이벤트 발행 연결
- 로컬 및 Terraform Kafka topic 목록 추가
- 기능 플래그와 재시도 설정, 테스트 및 연동 문서 추가

## 검증

결과:

- Java 및 테스트 코드 컴파일 성공
- 이벤트 발행·검증 단위 테스트 통과
- 실제 PostgreSQL/pgvector에서 현재 문서 저장과 오래된 이벤트 무시 확인
- 전체 518개 테스트 중 이번 변경 관련 테스트는 통과했다. 기존 채팅 통합 테스트 3개는 Redis handshake timeout 1건과 H2 `outbox_events` 초기화 문제 2건으로 실패했다.
- Terraform CLI가 로컬 PATH에 없어 `terraform fmt -check`는 실행하지 못했다.

## 이슈와 결정

- OpenAI 호출을 모임 생성·수정 트랜잭션에서 직접 실행하지 않고 Outbox 기반 비동기 처리로 분리했다.
- 현재 자동 배포에 영향을 주지 않도록 publisher와 consumer 기능 플래그 기본값을 `false`로 유지했다.
- 기존 `meeting_embeddings` 스키마로 오래된 이벤트를 차단할 수 있어 새 Flyway migration은 추가하지 않았다.

## 후속 작업

- 기존 모임 임베딩 백필 기능
- 검색 응답의 `retrievalMode`를 실제 실행 경로에 따라 `hybrid`로 전환
- AI 서버와 consumer 배포 후 기능 플래그 활성화 및 DLQ 모니터링
