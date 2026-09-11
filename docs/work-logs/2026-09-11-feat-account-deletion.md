# 작업 로그: feat/account-deletion

## 기본 정보

- 날짜: 2026-09-11
- 브랜치: `feat/account-deletion`
- 작업자: Codex
- 관련 PR: 생성하지 않음

## 사용자 요청

- Google Play 계정 삭제 정책 대응을 위한 앱 내 탈퇴 API와 공개 웹 삭제 요청 흐름 구현
- 기존 인증, 이메일 인증, Refresh Token, WebSocket, Push Token, 이미지 Outbox 흐름 재사용
- 운영 API, 운영 DB, AWS 배포 제외

## 작업 목표

- 현재 비밀번호 확인 또는 이메일 일회용 인증 후 동일한 탈퇴 정책을 트랜잭션으로 적용한다.
- 회원 FK를 보존하면서 개인정보와 자유 입력 식별값은 복구 불가능하게 익명화한다.
- 공개 정적 페이지와 최소 공개 API, Flyway 마이그레이션, 회귀 테스트를 제공한다.

## 작업 흐름

1. 회원과 모임, 참여, 북마크, 채팅, 알림, Push, 약관, 프로필 이미지 관계를 조사했다.
2. `deleted_at` 기반 탈퇴 상태와 목적 분리된 Redis 이메일 인증 저장소를 추가했다.
3. 앱/웹 탈퇴 서비스, 공개 페이지, 보안 경로와 테스트를 구현했다.

## 사용한 도구

- PowerShell
- `apply_patch`
- Gradle, Docker Compose, Testcontainers

## 실행한 주요 명령

```powershell
.\gradlew.bat test
docker compose up -d redis
docker compose stop redis
```

## 변경 파일 요약

- `domain/member`: 탈퇴 API, 서비스, 익명화 엔티티 상태
- `domain/auth`: 탈퇴 전용 이메일 코드/일회용 토큰과 메일 목적
- `domain/meeting`, `domain/chat`, `domain/notification`: 개인정보 정리 및 상태 처리
- `resources`: V16 Flyway, 공개 반응형 삭제 페이지, 설정
- `test`: API, 저장소, 서비스, 페이지, 세션 차단, 마이그레이션 테스트

## 검증

```powershell
.\gradlew.bat test
git diff --check
```

결과:

- 전체 460개 테스트 성공, 실패 0, 건너뜀 0
- 탈퇴 후 기존 Access Token의 STOMP 재접속 거부와 연결 중 세션 종료 검증
- 신규 DB에서 V16 적용 및 `ddl-auto=validate` 애플리케이션 컨텍스트 성공
- `git diff --check` 성공

## 이슈와 결정

- Member를 hard delete하지 않고 식별정보를 익명화해 기존 FK와 서비스 기록을 보존한다.
- 원 이메일은 익명값으로 치환되므로 탈퇴 후 같은 이메일로 재가입할 수 있다.
- 탈퇴 코드는 회원가입/비밀번호 재설정 코드와 Redis 키, 목적, 토큰을 분리했다.

## 후속 작업

- 실제 운영 배포 후 `https://api.meetple.shop/account-deletion` 접근성과 메일 전달을 확인한다.
- 고객지원 이메일과 법적 보존기간을 개인정보처리방침 담당자와 확정한다.
- 실제 Play Console의 계정 삭제 URL은 배포 검증 후 입력한다.
