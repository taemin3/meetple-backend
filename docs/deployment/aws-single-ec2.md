# AWS 단일 EC2 운영 배포

이 구성은 한 대의 EC2에서 Nginx, Spring Boot, PostgreSQL/PostGIS, Redis, Kafka를 Docker Compose로 실행한다. PostgreSQL, Redis, Kafka, Spring Boot 포트는 외부에 공개하지 않고 Nginx의 80/443만 공개한다.

## 1. AWS와 DNS 준비

1. Ubuntu EC2와 Elastic IP를 만든다. Kafka까지 같은 서버에서 실행하므로 메모리 8 GB 이상을 권장한다.
2. 보안 그룹 인바운드는 다음만 허용한다.
   - `22`: 관리자 IP에서만
   - `80`: 전체
   - `443`: 전체
3. 가비아 DNS에 A 레코드를 추가한다.
   - 호스트: `api`
   - 값: EC2 Elastic IP
   - 결과: `api.meetple.shop`
4. EC2에 Docker Engine과 Docker Compose 플러그인을 설치한다.

SES 도메인 인증용 DKIM CNAME 레코드는 API A 레코드와 별개다. SES에서 `meetple.shop` 도메인 인증을 완료하고, 임의 수신자에게 메일을 보내려면 SES 프로덕션 액세스도 승인받아야 한다.

## 2. EC2 IAM 역할

장기 AWS access key를 `.env.prod`에 저장하지 않는다. EC2 인스턴스 역할에 다음 S3 권한을 부여한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::meetple-images/images/*"
    }
  ]
}
```

이미지를 공개 URL로 직접 표시한다면 버킷 정책의 공개 읽기 범위도 `images/categories/*`, `images/meeting/*`, `images/profile/*`처럼 필요한 경로로 제한한다.

## 3. 운영 환경변수 작성

저장소를 받은 뒤 다음 파일을 만든다.

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

`.env.prod`의 빈 값을 모두 채운다. `JWT_SECRET`, `EMAIL_VERIFICATION_HMAC_SECRET`, DB와 Redis 비밀번호는 개발 값과 다르게 만든다.

```bash
openssl rand -base64 48
```

EC2 IAM 역할을 사용하면 `IMAGE_STORAGE_ACCESS_KEY`, `IMAGE_STORAGE_SECRET_KEY`는 빈 값으로 둔다. Firebase 푸시는 별도 운영 자격 증명과 Debezium Connect를 준비하기 전까지 비활성 상태를 유지한다.

## 4. 최초 HTTP 기동

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
```

Nginx는 인증서가 없으면 HTTP 프록시 설정으로 기동한다. DNS 전파 후 아래 요청이 성공하는지 확인한다.

```bash
curl http://api.meetple.shop/health
```

새 빈 DB에는 Flyway가 핵심 스키마부터 생성하고 기본 카테고리 `운동`, `스터디`, `취미`를 넣는다. 기존 현재 스키마를 가져왔지만 Flyway 이력이 없는 DB는 버전 11을 기준선으로 등록한다.

## 5. HTTPS 인증서 발급

HTTP 접근이 확인된 후 Let's Encrypt 인증서를 발급한다.

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml --profile certificate run --rm certbot
docker compose --env-file .env.prod -f docker-compose.prod.yml restart nginx
curl https://api.meetple.shop/health
```

Nginx를 다시 시작하면 인증서를 감지해 HTTPS 설정으로 전환하고 일반 HTTP 요청은 HTTPS로 이동시킨다.

## 6. 인증서 갱신

다음 두 명령을 EC2 cron 또는 systemd timer로 하루 한 번 실행한다. Certbot은 만료가 가까운 인증서만 실제 갱신한다.

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml --profile certificate run --rm certbot renew
docker compose --env-file .env.prod -f docker-compose.prod.yml exec nginx nginx -s reload
```

## 7. 배포 갱신과 확인

```bash
git pull --ff-only
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
curl https://api.meetple.shop/health
```

Flutter 운영 빌드는 API 주소를 같은 도메인으로 지정한다.

```bash
flutter build appbundle --dart-define=MEETPLE_API_BASE_URL=https://api.meetple.shop
```

## 8. 백업

운영 전 PostgreSQL 자동 백업과 EC2/EBS 장애 복구 절차를 정한다. 수동 백업 예시는 다음과 같다.

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > meetple-$(date +%F).dump
```

백업 파일은 같은 EC2 디스크에만 두지 말고 별도 S3 버킷에 보관하고, 복원 테스트도 수행한다.

## 운영 점검 목록

- `docker compose ps`에서 postgres, redis, kafka, backend가 정상 상태인지 확인
- API, WebSocket, 이메일 인증, 이미지 업로드/삭제를 실제 운영 주소에서 점검
- SES 반송률과 Kafka retry/DLQ 토픽 모니터링
- PostgreSQL, Kafka 볼륨과 EC2 메모리/디스크 사용량 알림 설정
- `.env.prod`, Firebase JSON, AWS 키가 Git에 포함되지 않았는지 확인
