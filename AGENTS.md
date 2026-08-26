# AGENTS.md

이 문서는 Codex가 `meetple-backend` 저장소를 다시 열었을 때 바로 이어서 작업하기 위한 기준이다.

## GitHub 저장소

- 저장소: https://github.com/taemin3/meetple-backend
- 이 프로젝트는 `C:\project\meetple\backend` 기준의 별도 Git 저장소이다.
- 커밋, 브랜치 생성, pull, push는 반드시 `backend/` 폴더에서 실행한다.

## 프로젝트 개요

`meetple-backend`는 운동, 스터디, 취미 모임 앱 meetple의 Spring Boot API 서버이다.

현재 백엔드의 목표:

- Flutter 앱과 연동할 REST API 제공
- 회원 인증과 프로필 API 제공
- `meeting` 기반 모임 생성, 조회, 수정, 취소, 완료 API 제공
- `meeting_participations` 기반 참여 신청, 승인, 거절, 취소 API 제공
- 지도 기반 주변 모임 검색을 위해 PostgreSQL + PostGIS 사용 예정
- 이후 Redis Pub/Sub + WebSocket 기반 채팅 확장

## 기술 스택

- Java 21
- Spring Boot 4
- Gradle
- Spring WebMVC
- Spring Data JPA
- Spring Validation
- PostgreSQL + PostGIS
- Redis
- H2 for test
- Docker Compose for local infra

## 명명 규칙

백엔드 도메인과 API에서는 `meeting`을 표준 이름으로 사용한다.

- Java package: `domain.meeting`
- Entity: `Meeting`, `MeetingParticipation`
- API path: `/api/v1/meetings`
- 참여 신청 path: `/api/v1/meetings/{meetingId}/participations`

## 현재 주요 구조

```text
src/main/java/com/meetple/backend/
  BackendApplication.java
  domain/
    auth/
      dto/request/
    category/
      entity/
      repository/
    health/
    meeting/
      dto/request/
      entity/
      repository/
    member/
      entity/
      repository/
  global/
    entity/
    exception/
    response/
src/main/resources/
  application.yml
  application-local.yml
  application-prod.yml
src/test/
  resources/application-test.yml
```

## 환경변수와 보안

- `application-local.yml`은 DB 계정/비밀번호를 환경변수에서만 읽는다.
- `application-prod.yml`은 운영 환경변수를 반드시 주입받아야 한다.

## Spring profile

- `local`: 로컬 PostgreSQL/PostGIS와 Redis 사용, `ddl-auto=update`
- `test`: H2 인메모리 DB 사용, `ddl-auto=create-drop`
- `prod`: 외부 환경변수 사용, `ddl-auto=validate`

테스트는 `@ActiveProfiles("test")`를 사용해 로컬 PostgreSQL에 의존하지 않게 한다.

## 공통 응답/예외 규칙

모든 API 응답은 `ApiResponse`를 사용한다.

공통 필드:

```text
status
success
code
message
data
```

사용 규칙:

- 성공 데이터 있음: `ApiResponse.success(SuccessStatus.OK, data)`
- 성공 데이터 없음: `ApiResponse.successOnly(SuccessStatus.OK)`
- 실패: 도메인 예외에서 `ErrorStatus`를 사용하고 `GlobalExceptionHandler`가 응답을 만든다.
- 새 예외 코드는 `ErrorStatus`에 먼저 추가한다.
- validation 실패는 `ErrorStatus.VALIDATION_ERROR`로 내려간다.
- `/livez`, `/readyz`, `/actuator/health/liveness`, `/actuator/health/readiness`는 ECS·로드 밸런서 등 인프라 프로브 전용 경로이므로 `ApiResponse` 적용 대상에서 제외하고, Actuator 표준 응답과 HTTP 상태 코드를 유지한다.

## ERD/도메인 규칙

핵심 엔티티:

- `Member`
- `Category`
- `Meeting`
- `MeetingParticipation`

보존해야 할 규칙:

- `members.email`은 unique
- `categories.name`은 unique
- `meeting_participations`는 `(meeting_id, member_id)` unique
- 같은 회원이 같은 모임에 중복 신청할 수 없다.
- 모임장은 자신의 모임만 수정, 취소, 완료할 수 있어야 한다.
- `COMPLETED`, `CANCELED` 상태의 모임에는 참여 신청을 막아야 한다.
- `currentPeople` 갱신은 승인/취소 흐름에서 트랜잭션과 동시성 처리를 고려한다.
- 위치 검색은 초기에는 `latitude`, `longitude`를 사용하되, PostGIS `geography(Point, 4326)` 확장을 고려한다.

상태 enum:

```text
MemberRole: USER, ADMIN
MeetingStatus: RECRUITING, FULL, COMPLETED, CANCELED
ParticipationStatus: PENDING, APPROVED, REJECTED, CANCELED
```

## API 기준

기본 prefix는 `/api/v1`을 사용한다.

예정 API:

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/reissue`
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me`
- `DELETE /api/v1/users/me`
- `GET /api/v1/users/me/meetings/hosted`
- `GET /api/v1/users/me/meetings/joined`
- `GET /api/v1/users/me/applications`
- `GET /api/v1/categories`
- `POST /api/v1/meetings`
- `GET /api/v1/meetings`
- `GET /api/v1/meetings/nearby`
- `GET /api/v1/meetings/{meetingId}`
- `PATCH /api/v1/meetings/{meetingId}`
- `DELETE /api/v1/meetings/{meetingId}`
- `PATCH /api/v1/meetings/{meetingId}/complete`
- `PATCH /api/v1/meetings/{meetingId}/cancel`
- `POST /api/v1/meetings/{meetingId}/participations`
- `GET /api/v1/meetings/{meetingId}/participations`
- `PATCH /api/v1/participations/{participationId}/approve`
- `PATCH /api/v1/participations/{participationId}/reject`
- `PATCH /api/v1/participations/{participationId}/cancel`

목록 조회 API는 `page`, `size`, `sort`를 고려한다.

## Git/GitHub 규칙

자세한 규칙은 `CONTRIBUTING.md`를 따른다.

요약:

- `main`에 직접 push하지 않는다.
- 기능 브랜치에서 작업하고 PR로 병합한다.
- 브랜치 이름은 `type/short-description` 형식으로 만든다.
- 커밋 메시지는 Conventional Commits 스타일을 사용한다.
- PR 병합은 기본적으로 `Squash and merge`를 사용한다.

### Codex 작업 흐름

- Codex는 요청받은 작업을 별도 브랜치에서 구현하고 테스트한다.
- Codex는 변경 내용을 커밋하고 원격 브랜치에 푸시한 뒤 PR 생성 링크를 전달한다.
- PR 생성, 리뷰 요청, 머지는 사용자가 직접 진행한다.
- 사용자가 명시적으로 요청한 경우에만 Codex가 PR을 생성한다.
- 머지는 사용자가 최종 검토 후 직접 수행한다.

### PR 작성 정보 제공

Codex는 원격 브랜치 푸시 후 사용자가 PR을 직접 만들 수 있도록 최종 응답에 아래 내용을 포함한다.

- 브랜치 이름
- 커밋 해시와 커밋 메시지
- PR 생성 링크
- PR 제목 초안
- PR 본문 초안
- 테스트 결과
- 리뷰 시 확인하면 좋은 포인트

PR 생성은 사용자가 직접 하더라도, Codex는 사용자가 GitHub PR 화면에 바로 붙여넣을 수 있는 형태로 제목과 본문을 제공한다.
PR 본문 초안은 가능하면 아래 구조를 따른다.

```markdown
## 작업 내용
- 핵심 변경 사항을 2~4개로 요약

## 테스트
- 실행한 테스트 명령과 결과
- 실행하지 못한 검증이 있으면 그 이유

## 리뷰 포인트
- 리뷰어가 특히 확인하면 좋은 부분
- 보안, 인증, 트랜잭션, Redis, DB 스키마처럼 주의가 필요한 지점
```

PR 리뷰 반영 커밋을 푸시한 경우에는 어떤 리뷰를 수용했고 무엇을 고쳤는지 짧게 요약한다.

예시:

```text
feat/member-signup
feat/meeting-create
fix/env-config
docs/api-spec
refactor/response-format
```

커밋 예시:

```text
feat: meeting 엔티티와 repository 추가
fix: local profile 환경변수 로딩 수정
docs: 백엔드 Codex 작업 가이드 추가
test: meeting repository 테스트 추가
```

## 검증 명령

백엔드 작업 후 가능한 경우 아래 명령을 실행한다.

```bash
./gradlew test
```

로컬 인프라가 필요한 경우:

```bash
docker compose up -d
```

Docker가 설치되어 있지 않거나 PATH에 없을 수 있다. 이 경우 Docker 관련 검증은 못 했다고 명확히 말한다.

## 작업 원칙

- 기존 사용자가 만든 변경을 되돌리지 않는다.
- 미커밋 변경이 있으면 먼저 `git status --short --branch`로 확인한다.
- 관련 없는 변경과 섞어 커밋하지 않는다.
- Entity/API/DTO 변경은 테스트를 같이 추가하거나 갱신한다.
- 응답 포맷은 `ApiResponse` 계약을 유지한다.
- 새 API를 만들 때는 controller, request/response DTO, service, repository, test 순서로 작게 진행한다.
- 대규모 리팩터링보다 도메인 단위의 작은 PR을 선호한다.

## 작업 로그 규칙

Codex가 브랜치 단위로 파일을 수정하고 커밋/푸시하는 작업을 수행하면 가능하면 `docs/work-logs/`에 작업 로그를 남긴다.

작업 로그는 `docs/work-logs/TEMPLATE.md`를 기준으로 작성한다.

로그에는 다음 내용을 포함한다.

- 사용자 요청 요약
- 브랜치명
- 작업 목표와 작업 흐름
- 사용한 도구
- 실행한 주요 명령
- 변경 파일 요약
- 검증 명령과 결과
- 작업 중 발견한 이슈와 결정 사항
- 후속 작업

민감한 값은 기록하지 않는다.

- access token, refresh token, API key, 비밀번호, 개인 정보는 남기지 않는다.
- `.env` 값, 로컬 DB/Redis 비밀번호, 운영 환경 변수 값은 남기지 않는다.
- 긴 명령 출력은 전체를 붙이지 말고 핵심 결과만 요약한다.
- 실패한 명령은 원인 추적에 필요한 경우 명령과 실패 이유를 요약한다.
- PR 본문은 짧게 유지하고, 자세한 작업 흐름은 작업 로그에 남긴다.

파일 이름은 날짜와 브랜치명을 사용한다.

```text
docs/work-logs/YYYY-MM-DD-branch-name.md
```

예시:

```text
docs/work-logs/2026-05-26-feat-meeting-participation.md
```

## PR Review Language

- 모든 PR 리뷰 요약과 코멘트는 한국어로 작성한다.
- 코드 식별자, 파일명, 클래스명, 메서드명은 원문 영어를 유지한다.
- 리뷰는 짧고 명확하게 작성하고, 중요한 버그/보안/테스트 누락을 먼저 지적한다.

## 백엔드 GitHub 작업 방식
- Codex는 브랜치 생성, 커밋, 푸시까지 수행
- PR 생성/머지는 사용자가 직접 수행
- PR 리뷰 반영은 꼭 필요한 것만 최소 수정

## 커밋 메시지 규칙
- feat: 기능 추가
- fix: 버그 수정
- test: 테스트 추가/수정
- docs: 문서 수정
- refactor: 동작 변경 없는 개선

## 문서/Swagger 규칙
- Swagger 설명은 한국어로 작성
- PR 리뷰/요약도 한국어

