# 작업 로그: perf/chat-outbound-session-authorization

## 사용자 요청

- 채팅 구독자별 Redis·DB 권한 검증을 줄이고 무효화 계약을 유지한다.
- 로컬 부하 결과를 재현 가능한 근거로 정리한다.
- 기존 데이터와 원시 성능 파일은 Git에 포함하거나 삭제하지 않는다.

## 작업 목표와 흐름

1. CONNECT/SUBSCRIBE 시 검증된 세션·방 정보를 로컬 registry에 저장했다.
2. outbound MESSAGE는 JWT 만료와 정확한 STOMP subscription을 로컬에서 확인한다.
3. 30초 TTL 만료 시에만 Redis 토큰 상태와 DB 방 접근 권한을 다시 확인한다.
4. 기존 Redis Pub/Sub 세션 무효화 흐름을 유지했다.
5. focused/distributed 및 구독자 10/1 조건을 로컬에서 비교했다.

## 주요 변경

- `AuthenticatedAccessToken`에 JWT 만료 시각 전달
- `LocalChatWebSocketSessionRegistry`에 구독 검증 시각과 TTL 상태 저장
- outbound 권한 조회 시 `sessionId`, `subscriptionId`, `roomId`를 함께 검증
- 권한 철회 시 해당 방 구독 제거, 토큰 무효/만료 시 세션 제거
- 실제 Redis·DB 재검증을 `outboundAuthRefresh`로 별도 계측
- 순번 잠금 획득 이후 서비스 반환과 실제 커밋까지의 시간을 분리 계측

## 검증

- 핵심 WebSocket/계측 테스트 26개: 성공
- `gradlew.bat test --no-daemon`: 478개 성공
- 모든 유효 100건/s 실행: 3,000 scheduled/attempted/committed/received, 관련 오류·누락·중복 0
- AWS/staging 배포 및 실제 FCM 발송: 수행하지 않음

## 결정과 한계

- TTL 내 전달은 로컬 검증만 수행하지만 기존 로그아웃·회원 탈퇴·참여 취소·권한 변경 invalidation을 유지한다.
- invalidation 누락에 대비해 30초 후 Redis·DB를 재검증한다.
- 동일 방 순번 행 잠금은 제거하지 않았으며 지속 100 msg/s의 남은 병목으로 기록한다.
- 원시 JSON/JSONL, manifest, JFR 및 로그는 로컬에만 보존한다.
