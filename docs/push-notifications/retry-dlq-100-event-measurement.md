# 100건 Push Retry / DLQ / 재처리 측정 절차

이 문서는 실험 계획이다. 테스트 Sender, 이벤트 추적 로그, 입력 및 재처리 도구는 아직 이 변경에서 구현하지 않았다. 실제 측정 결과를 의미하지 않는다.

## 준비

- 로컬 PostgreSQL, Kafka, Debezium과 별도 테스트 프로필의 앱을 사용한다.
- 테스트 프로필에서만 실제 FCM Sender를 테스트 Sender로 교체한다. 실제 기기에는 발송하지 않는다.
- 테스트 Sender는 FAIL 모드에서 기기별 `PushSendFailure`를 담은 `PushSendResult`를 반환한다. SUCCESS 모드에서는 대상 ID를 `sentTargetIds`로 반환한다.
- FAIL 결과도 기존 `record()` 경로를 거쳐 claim을 해제하도록 한다. 임의 예외로 결과 기록을 생략하는 실험과 구분한다.
- 이벤트마다 대상 기기 1개를 사용한다. Sender에는 eventId가 message.data에 포함된다.
- 애플리케이션을 재시작하지 않고 Sender 모드를 바꾸는 로컬 테스트 제어 수단을 준비한다.
- 입력 도구는 업무 트랜잭션을 통해 Outbox에 100건을 커밋하고, 커밋 후 eventId 목록을 해당 실행의 manifest로 저장한다. API 요청 수를 이벤트 수로 간주하지 않는다.
- 다른 실험과 겹치지 않는 runId와 eventId를 사용한다. 모든 분석은 manifest의 ID로 제한한다.
- 앱 시작 시 실제 적용된 배수가 4.0인지 확인한다. 환경변수가 기본값을 덮어쓸 수 있다.

## 기록

구조화 로그에는 runId, eventId, deviceId, stage, timestamp, topic, partition, offset, result를 기록한다. 토큰이나 payload 본문은 기록하지 않는다.

- Consumer 진입: CONSUME
- Sender 호출 시작 및 완료: SEND_START / SEND_END
- 전송 결과 DB 기록 성공 후: RECORD_END
- DLQ 관측: DLQ
- 재발행 브로커 확인 후: REPLAY_ACK

발송 성공 횟수는 테스트 Sender의 호출별 기록으로 집계한다. DB의 SENT 행 수만으로 중복 외부 발송이 없었다고 판정하지 않는다. 로그가 완전히 저장됐는지도 확인한다.

## 실행

1. Sender를 FAIL 모드로 설정한다.
2. 초당 1건씩 100건의 이벤트를 생성하고 커밋된 ID 100개를 확인한다.
3. DLQ를 별도 관측 consumer group으로 읽어 manifest의 고유 ID를 수집한다. 기존 DLQ handler가 소비했더라도 보존된 레코드를 별도 그룹으로 읽을 수 있다.
4. 마지막 커밋부터 최대 10분을 관측 한도로 정한다. 100개가 모두 도착하면 다음 단계로 진행한다. 한도에 도달하면 미도달 ID, Main/Retry 적체 및 오류를 기록하고 성공 판정을 하지 않는다.
5. Sender를 SUCCESS 모드로 바꾼다.
6. 수집한 DLQ 레코드 중 manifest에 속하는 ID별 한 건을 선택한다. 원래 topic으로 key와 value를 그대로 재발행하고, 과거 retry/exception 제어 헤더는 복사하지 않는다. 새 eventId를 만들거나 전송 이력을 지우지 않는다.
7. 마지막 재발행 확인부터 최대 10분 안에 대상 100개가 모두 SENT가 되는지 관측한다. 재처리 도구는 재발행한 ID와 브로커 확인 결과를 기록해 실수로 반복 발행하지 않도록 한다.
8. manifest, 전체 로그, DLQ 관측 기록, 재발행 기록, 전송 이력 스냅샷을 함께 보관한다.

현재 기본 지연은 1 / 4 / 16 / 64초로 합계 85초다. 초당 1건 입력 시 입력 자체에 약 100초가 걸리므로 전체 실험이 85초에 끝나는 것은 아니다. 실제로는 처리시간과 적체가 더해진다.

## 집계

| 지표 | 계산 / 정상적인 실험의 기대값 |
| --- | --- |
| 커밋된 이벤트 | manifest의 고유 ID: 100개 |
| DLQ 도달 | manifest와 DLQ 고유 ID 교집합: 100개 |
| DLQ 미도달 | manifest에서 DLQ 관측 ID를 뺀 집합: 0개 |
| 실패 발송 시도 | 각 이벤트 5회, 총 500회 예상. 중복 소비 등으로 다르면 실제 값을 보고 원인 분석 |
| 재처리 후 성공 대상 | manifest 이벤트별 대상 기기의 SENT: 100개 |
| 중복 성공 발송 | 각 (eventId, deviceId)의 Sender 성공 횟수에서 최초 1회를 제외한 합계: 0건 기대 |
| DLQ 소요시간 | 각 이벤트 최초 Consumer 진입부터 최초 DLQ 관측까지 |
| 재처리 완료시간 | 첫 재발행 시작부터 마지막 대상의 성공 결과 DB 기록까지 |

이 실험은 정상적으로 실패 결과를 기록하는 상황의 재시도 및 명시적 DLQ 재처리를 검증한다. CDC 중단 복구, Consumer 강제 종료, 실제 FCM 수신 및 전체 업무 API 성능을 검증한 결과로 확대 해석하지 않는다.

프로세스 종료로 claim이 남은 경우에는 5분 lease 만료 전에 85초 재시도를 소진할 수 있다. 해당 실험과 복구는 별도로 수행한다.
