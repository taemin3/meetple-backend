# 작업 로그: perf/chat-performance-improvement

## 사용자 요청

- 측정 전용 코드를 운영 코드와 분리한다.
- 로컬에서 검증한 실제 채팅 성능 개선과 필요한 테스트만 clean 브랜치로 구성한다.

## 브랜치

- `perf/chat-performance-improvement`
- 기준: 최신 `origin/main`의 `18ff1e9`
- 측정 원본: `perf/chat-outbound-session-authorization`의 `5528d74`

## 포함한 변경

- 방별 `chat_room_sequences` 순번 행과 V19 migration
- meeting 공유 잠금과 순번 행 쓰기 잠금을 이용한 임계 구역 축소
- STOMP inbound/outbound worker 4개와 bounded queue
- 세션/구독 권한 로컬 캐시, JWT 만료 확인, 30초 TTL 재검증
- 로그아웃·회원 탈퇴·참여 취소·권한 변경 invalidation 유지
- 기능·마이그레이션·WebSocket 설정 테스트
- 로컬 측정 결과 요약 문서

## 제외한 변경

- 측정 Controller와 Recorder
- 서비스·interceptor·fan-out listener의 측정 hook
- 로컬 fixture, 부하 실행기와 잠금 sampler
- 원시 JSON/JSONL, JFR, manifest 및 로그

## 검증

- `gradlew.bat compileJava compileTestJava --no-daemon`: 성공
- `gradlew.bat test --no-daemon`: 473개 성공
- 실제 PostgreSQL 기반 fresh migration/application context 테스트: 성공
- AWS/staging 배포와 실제 FCM 발송은 수행하지 않는다.
