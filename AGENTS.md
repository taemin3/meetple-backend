# AGENTS.md

이 문서는 Codex가 `meetple-backend` 저장소를 다시 열었을 때 바로 이어서 작업하기 위한 기준이다.

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

Flutter 목업 쪽에는 아직 `meetup` 이름이 남아 있을 수 있지만, 백엔드에서는 새 코드에 `meetup`을 쓰지 않는다.

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

- `.env`는 절대 커밋하지 않는다.
- `.env.example`은 GitHub에 올려도 되지만, 실제 값은 비워둔다.
- DB 비밀번호, 토큰, 개인 로컬 설정은 코드/문서/커밋 메시지에 남기지 않는다.
- 로컬 Spring Boot는 `application.yml`의 `optional:file:.env[.properties]` 설정으로 `backend/.env`를 읽는다.
- `application-local.yml`은 DB 계정/비밀번호를 환경변수에서만 읽는다.
- `application-prod.yml`은 운영 환경변수를 반드시 주입받아야 한다.

로컬 `.env` 예시는 사용자가 직접 관리한다. 실제 값이 필요하면 사용자에게 묻거나 현재 파일을 확인하되, 최종 답변에 비밀번호를 그대로 쓰지 않는다.

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

## 다음 작업 우선순위

1. `MemberService`와 회원가입 기본 흐름
2. 비밀번호 암호화와 Spring Security/JWT 기반 인증
3. `MeetingService`와 모임 생성 API
4. 모임 목록/상세/주변 검색 API
5. 참여 신청, 승인, 거절, 취소 API
6. Refresh Token 저장 전략 결정
7. WebSocket/Redis 채팅 확장
