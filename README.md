# meetple backend

Spring Boot 기반 meetple API 서버입니다.

## 현재 스택

- Spring Boot 4
- PostgreSQL + PostGIS
- Redis Pub/Sub
- WebSocket 예정
- Docker Compose

## 로컬 인프라 실행

PostgreSQL/PostGIS와 Redis는 Docker Compose로 실행합니다.

```bash
cd backend
docker compose up -d
```

로컬 접속 정보는 `.env.example`을 복사한 `.env`에만 입력합니다.

- PostgreSQL: `localhost:15432`
- database: `meetple`
- Redis: `localhost:6379`

`.env`는 Git에 올리지 않습니다. Spring Boot는 `local` profile에서 `backend/.env`를 optional config로 읽습니다.

## Spring profile

기본 profile은 `local`입니다.

- `local`: 로컬 Docker Compose의 PostgreSQL/PostGIS를 사용하고 `ddl-auto=update`를 적용합니다.
- `test`: H2 인메모리 DB를 사용합니다.
- `prod`: 환경변수로 DB 접속 정보를 반드시 주입하고 `ddl-auto=validate`를 적용합니다.

로컬 실행:

```bash
cd backend
./gradlew bootRun
```

테스트:

```bash
cd backend
./gradlew test
```
