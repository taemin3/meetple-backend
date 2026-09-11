# 작업 로그: feat/privacy-policy

## 기본 정보

- 날짜: 2026-09-12
- 브랜치: `feat/privacy-policy`
- 작업자: Codex
- 관련 PR: 생성 전

## 사용자 요청

- 회원 탈퇴 기능 구현 후 개인정보처리방침을 실제 앱과 외부 웹에서 확인할 수 있도록 정리한다.

## 작업 목표

- 현재 코드와 인프라 설정을 기준으로 개인정보 처리 항목, 목적, 보유 기간, 위탁·국외 이전, 탈퇴 절차를 문서화한다.
- 인증 없이 접근 가능한 개인정보처리방침 웹페이지를 제공한다.
- 앱의 기존 약관 API가 최신 정책 버전을 제공하도록 이력을 추가한다.
- 회원 탈퇴 페이지에서 개인정보처리방침으로 연결한다.

## 작업 흐름

1. 회원, 모임, 채팅, 알림, 위치, FCM, 인증과 탈퇴 코드를 기준으로 데이터 흐름을 확인했다.
2. AWS 로그·백업 보존 설정과 Google Play/Firebase 및 개인정보보호위원회 공식 안내를 대조했다.
3. `2026-09-12` 정책 버전과 공개 웹페이지, 공개 라우팅을 추가했다.
4. 회원 탈퇴 페이지의 임시 운영 문구를 제거하고 정책 링크를 연결했다.
5. 공개 접근 테스트와 데스크톱·모바일 레이아웃 검사를 실행했다.

## 사용한 도구

- `rg`, PowerShell
- `apply_patch`
- Gradle Test
- Playwright headless layout inspection

## 실행한 주요 명령

```powershell
.\gradlew.bat test --tests "com.meetple.backend.domain.legal.controller.PrivacyPolicyPageTest" --tests "com.meetple.backend.domain.member.controller.AccountDeletionPageTest"
.\gradlew.bat test
git diff --check
```

## 변경 파일 요약

- `V17__publish_privacy_policy.sql`: 최신 개인정보처리방침 이력 추가
- `privacy-policy/index.html`: 공개 정책 페이지 추가
- `PrivacyPolicyPageController.java`, `SecurityConfig.java`: 공개 라우팅과 인증 예외 추가
- `LocalLegalDocumentInitializer.java`: 로컬 프로필 최신 정책 제공
- `account-deletion/index.html`: 정책 링크 연결과 임시 문구 제거
- 정책 운영 메모와 공개 페이지 테스트 추가

## 검증

```text
대상 테스트: BUILD SUCCESSFUL, 2개 페이지 테스트 통과
레이아웃 검사: 1280px/390px에서 body 가로 넘침 없음, 10개 정책 섹션과 링크 확인
git diff --check: 통과(줄바꿈 변환 경고만 존재)
전체 테스트: 466개 중 29개 실패, 6개 skipped
```

결과:

- 추가·변경한 공개 페이지 테스트는 통과했다.
- 전체 테스트의 29개 실패는 로컬 `127.0.0.1:6379` Redis 연결 실패로 발생했으며 이번 변경 파일과 직접 관련되지 않는다.

## 이슈와 결정

- 앱은 이미 약관 API의 최신 문서를 가입 화면과 프로필 화면에 표시하므로 앱 저장소 변경은 하지 않았다.
- Google FCM은 글로벌 인프라에서 처리될 수 있으므로 미국 한 국가로 단정하지 않고 공식 Firebase 안내 범위로 기재했다.
- 저장소에서 실제 법적 운영자 실명을 확인할 수 없어 공개 본문에는 `밋플 운영팀`을 사용하고 배포 전 확인 항목으로 남겼다.

## 후속 작업

- Play Console의 개발자 실명과 개인정보 처리자 명칭을 최종 대조한다.
- 실제 Firebase 계약·설정에 따른 국외 처리 국가와 설치 식별자 삭제 절차를 확인한다.
- 정책 URL 배포 후 Play Console 개인정보처리방침 URL과 데이터 보안 설문을 갱신한다.
