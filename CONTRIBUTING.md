# Contributing

meetple 백엔드 저장소의 Git/GitHub 작업 규칙입니다.

## 기본 원칙

- `main` 브랜치에 직접 push하지 않습니다.
- 모든 작업은 기능 브랜치에서 진행하고 Pull Request로 병합합니다.
- PR 병합은 기본적으로 `Squash and merge`를 사용합니다.
- 하나의 PR은 하나의 목적만 다룹니다.
- `.env`, 로컬 설정, 개인 토큰, DB 비밀번호는 커밋하지 않습니다.

## 브랜치 규칙

브랜치 이름은 `type/short-description` 형식을 사용합니다.

```text
feat/member-signup
feat/meeting-create
feat/meeting-participation
fix/env-config
docs/api-spec
refactor/response-format
chore/gradle-config
```

주요 type:

```text
feat      새 기능
fix       버그 수정
docs      문서 수정
test      테스트 추가/수정
refactor  동작 변화 없는 구조 개선
style     포맷팅만 변경
chore     빌드, 설정, 잡일
ci        GitHub Actions 같은 CI 설정
```

## 커밋 메시지 규칙

Conventional Commits 스타일을 사용합니다.

```text
feat: meeting 엔티티와 repository 추가
fix: local profile 환경변수 로딩 수정
docs: Git 작업 규칙 추가
test: meeting repository 테스트 추가
refactor: 공통 응답 구조 정리
chore: Gradle 설정 정리
```

형식:

```text
type: 변경 내용 요약
```

요약은 짧게 쓰고, 무엇을 바꿨는지 바로 알 수 있게 작성합니다.

## 작업 흐름

```bash
git checkout main
git pull origin main
git checkout -b feat/example

# 작업 후
git status
git add .
git commit -m "feat: example 기능 추가"
git push -u origin feat/example
```

GitHub에서 PR을 생성하고, 확인 후 `main`으로 병합합니다.

## PR 체크리스트

- 변경 목적이 PR 제목과 설명에 드러나는가?
- API 변경이 있으면 요청/응답 DTO와 문서를 같이 확인했는가?
- Entity 변경이 있으면 제약조건과 테스트를 확인했는가?
- `./gradlew test`를 실행했는가?
- `.env`, 비밀번호, 토큰, 개인 로컬 설정이 포함되지 않았는가?
- 불필요한 빌드 산출물, IDE 파일, 로그가 포함되지 않았는가?

## 백엔드 검증 명령

가능한 경우 아래 순서로 확인합니다.

```bash
./gradlew test
```

Docker가 필요한 로컬 인프라 확인:

```bash
docker compose up -d
```
