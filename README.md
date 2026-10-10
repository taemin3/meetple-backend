# Meetple Backend

> 모임·채팅·신고 도메인과 이벤트 파이프라인, AWS 배포 인프라를 담당하는 Spring Boot API 서버

Meetple 전체 구성과 저장소 링크는 [프로젝트 허브](https://github.com/taemin3/meetple)에서 확인할 수 있습니다.

## 주요 역할

- 이메일 인증, JWT 로그인·재발급, 프로필과 계정 삭제
- PostGIS 기반 주변 모임 탐색, 모임 생성·수정, 참여 신청과 승인·거절·취소
- STOMP WebSocket 채팅, 메시지 영속화, Redis Pub/Sub fan-out과 재연결 복구
- S3 이미지 업로드와 삭제, Firebase 푸시 및 이메일 알림
- 사용자·모임·채팅 신고, AI 신고 분석 수명주기, 관리자 제재와 복구
- 운영 정책 버전 관리, pgvector 기반 정책 검색과 임베딩 동기화
- Transactional Outbox, Debezium, Kafka Retry/DLQ 기반 비동기 처리
- ECS·RDS·S3·CloudFront·모니터링을 포함한 Terraform staging 구성

현재 프로젝트 소개 범위에서는 자연어 모임 검색을 제외하고, AI 연동을 운영 정책 기반 신고 분석에 사용합니다.

## 기술 구성

| 구분 | 기술 |
| --- | --- |
| Application | Java 21, Spring Boot 4, Spring WebMVC, Spring Security, Spring Data JPA |
| Data | PostgreSQL, PostGIS, pgvector, Flyway |
| Realtime | STOMP WebSocket, Redis Pub/Sub |
| Event | Transactional Outbox, Debezium, Kafka |
| External | AWS S3·CloudFront, Firebase Cloud Messaging, SMTP |
| Infrastructure | Docker Compose, Terraform, AWS ECS EC2, ALB, RDS, CloudWatch |
| Test | JUnit 5, H2, Testcontainers |

## 서비스 흐름

```text
Flutter App / Admin SPA
        │ REST / WebSocket
        ▼
Spring Boot Backend ────────────── PostgreSQL · PostGIS · pgvector
        │                         Redis Pub/Sub
        │ Outbox
        ▼
Debezium → Kafka → Push · Email · Image Delete · AI Report Analysis
                                                │
                                                └─ 분석 결과 callback
```

사용자 요청의 권한과 최종 제재는 Spring이 결정합니다. AI는 신고 증거와 운영 정책을 바탕으로 검토 정보를 만들지만 회원 정지나 모임 삭제를 직접 실행하지 않습니다.

## 로컬 실행

Java 21과 Docker를 준비하고 `.env.example`을 복사한 뒤 비어 있는 로컬 값을 채웁니다. 실제 `.env`와 인증 정보는 커밋하지 않습니다.

```powershell
Copy-Item .env.example .env
docker compose up -d
.\gradlew.bat bootRun
```

Docker Compose는 PostgreSQL/PostGIS, Kafka, Kafka Connect/Debezium, Kafka UI, Redis, Mailpit을 제공합니다. Kafka consumer와 FCM 같은 외부 연동은 `.env`에서 명시적으로 활성화한 경우에만 동작합니다.

### Spring profile

- `local`: 로컬 PostgreSQL/PostGIS·Redis 사용, `ddl-auto=update`
- `test`: 테스트 전용 설정 사용
- `prod`: 외부 환경변수 필수, Flyway 사용, `ddl-auto=validate`

## 검증

```powershell
.\gradlew.bat test
```

외부 인프라를 사용하는 테스트는 Docker, Redis 또는 별도 환경이 필요할 수 있습니다. 테스트 통과만으로 실제 Firebase 기기 전달이나 AWS 배포가 검증됐다고 보지 않습니다.

## 관련 저장소

- [Meetple App](https://github.com/taemin3/meetple-app)
- [Meetple AI](https://github.com/taemin3/meetple-ai)
- [Meetple Admin](https://github.com/taemin3/meetple-admin)
