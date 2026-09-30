# AI 모임 검색 연동

Python AI 서버는 독립 저장소 [meetple-ai](https://github.com/taemin3/meetple-ai)에서 관리한다. 실행·평가·API 계약은 [AI 서버 README](https://github.com/taemin3/meetple-ai/blob/main/README.md)를 참고한다.

권장 로컬 폴더는 `C:\project\meetple\app`, `backend`, `ai`다. Spring은 `backend`에서 실행한다. AI 기능이 main에 병합되기 전에는 `feat/ai-search-foundation` 브랜치에서 연동을 검증한다.

## 백엔드에 남는 역할

- `POST /api/v1/meetings/ai-search`: 로그인 검증 후 AI 서버 호출.
- `/internal/ai/search/categories`, `/internal/ai/search/meetings`: 서비스 키와 단기 서명을 검증한 뒤 DB 조회. 모임 검색은 선택적으로 1536차원 `queryEmbedding`을 받는다.
- 차단·탈퇴·모집 상태와 날짜·시간·거리 검색 조건 적용, 최종 추천 ID·원문 근거 재검증.
- Python 의존성·CI·Docker 이미지는 AI 저장소에서 관리한다. pgvector 스키마와 검색 SQL은 백엔드에서 관리한다.

## Spring 설정

| 환경변수 | 기본값/역할 |
| --- | --- |
| `AI_SEARCH_ENABLED` | `false`; 연동 실행 시 `true` |
| `AI_SEARCH_BASE_URL` | `http://127.0.0.1:8001` |
| `AI_SEARCH_SERVICE_TOKEN` | AI 서버의 `AI_SERVICE_TOKEN`과 같은 32자 이상 키 |
| `AI_SEARCH_CAPABILITY_SECRET` | 서비스 키와 다른 32자 이상 서명 키. Spring에만 설정 |
| `AI_SEARCH_TIMEOUT` | `45s`, 최대 `60s` |

AI 서버의 `AI_BACKEND_URL`에는 이 Spring 서버의 주소를 지정한다. 폴더 위치와 관계없이 HTTP로 연결한다. Spring은 AI 레포의 `.env`를 자동으로 읽지 않는다.

사용자 JWT는 AI 서버에 전달하지 않는다. 서명 권한은 90초간 유효하다. Python 및 내부 조회 경로는 사설 네트워크로 연결하고 공개 ingress에서 차단한다.

`queryEmbedding`이 있으면 구조 조건을 먼저 적용한 뒤 키워드 일치 점수 45%와 pgvector 코사인 유사도 55%로 후보를 정렬한다. 임베딩이 없는 모임도 키워드가 일치하면 후보에서 제외하지 않는다. 모임 생성·수정 시 임베딩 갱신과 기존 데이터 백필은 후속 범위이므로, 그 작업 전에는 저장된 벡터가 있는 모임에만 의미 점수가 적용된다. 운영 활성화 전 사용자별 요청/비용 제한이 필요하다.

검색은 대화 상태를 저장하지 않는 단일 요청 방식이다. 일반적인 선호 표현은 AI가 자연스럽게 해석하고, Spring은 날짜·시간·거리와 권한을 다시 검증한다. 오전은 06:00~12:00, 오후는 12:00~18:00, 저녁은 18:00 이후다. 위치가 없으면 `INPUT_REQUIRED`, 다른 지역·일정 충돌·생성/참여 요청은 `UNSUPPORTED`를 반환한다.

## 테스트

```powershell
.\gradlew.bat test --tests 'com.meetple.backend.domain.ai.*'
.\gradlew.bat test
```

PostGIS·pgvector 통합 테스트에는 Docker와 `meetple-postgres:16-3.5-bigm-vector0.8.6` 이미지, 전체 테스트에는 Redis가 필요하다. Python 테스트는 AI 저장소에서 실행한다.
