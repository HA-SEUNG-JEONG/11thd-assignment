# Project Collab

프로젝트와 작업을 여러 사용자가 함께 관리하는 협업 서비스 백엔드.

```bash
./gradlew bootRun          # 첫 실행 약 1~2분(JDK·의존성 조달), 이후 기동 약 2초
open http://localhost:8080/swagger-ui.html
```

Swagger UI의 `X-User-Id`에 `1`을 넣고 `GET /api/projects`를 실행하면 프로젝트 1건이 나옵니다.

Java 17 · Spring Boot 3.3.13 · JPA(Hibernate) · H2 인메모리 · Gradle 8.14.5 · OpenAPI 3

명세가 비워둔 지점을 어떻게 결정했고 어떤 대안을 왜 기각했는지는 [주요 설계 결정과 그 이유](#주요-설계-결정과-그-이유)에 있습니다.

---

## 빌드 &amp; 실행

```bash
git clone <repository-url>
cd 11thd-assignment
./gradlew bootRun          # 첫 실행 약 1~2분(JDK·의존성 조달), 이후 기동 약 2초
```


| 항목             | 주소                                                                                                                      |
| -------------- | ----------------------------------------------------------------------------------------------------------------------- |
| **Swagger UI** | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)                                          |
| OpenAPI 문서     | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)                                                  |
| H2 콘솔          | [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL `jdbc:h2:mem:collab`, 사용자 `sa`, 비밀번호 없음) |


로컬에 JDK 17이 없어도 됩니다. Gradle toolchain(`languageVersion 17`) + foojay-resolver가 빌드 시점에 JDK 17을 조달합니다.

테스트: `./gradlew test` — 24개, 약 10초.

---

## 실행 직후 기능 확인 절차

### 초기 데이터 (`src/main/resources/data.sql`)


| 사용자   | id  | 프로젝트 1 · 스터디 플랫폼 개편 | 프로젝트 2 · 사내 위키 이관 |
| ----- | --- | ------------------- | ----------------- |
| alice | 1   | **OWNER**           | 비멤버               |
| bob   | 2   | MEMBER              | 비멤버               |
| carol | 3   | 비멤버                 | **OWNER**         |


기동할 때마다 위 데이터가 새로 적재됩니다. 작업은 프로젝트 1에 4건(id 1~4), 프로젝트 2에 1건(id 5)이며, 5번은 프로젝트 경계 검증용입니다.

`X-User-Id` 헤더로 요청자를 식별합니다. Swagger UI는 모든 오퍼레이션에 입력란을 자동 노출합니다.

### 권한 규칙을 한 번에 확인하는 시나리오

```bash
# 1. 내가 속한 프로젝트만 나온다 — alice에게 프로젝트 2는 보이지 않는다
curl -s -H 'X-User-Id: 1' localhost:8080/api/projects

# 2. 비멤버는 404 — "존재하지만 권한이 없다"조차 알려주지 않는다
curl -s -o /dev/null -w '%{http_code}\n' -H 'X-User-Id: 1' localhost:8080/api/projects/2   # 404

# 3. 멤버지만 역할이 부족하면 403
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE -H 'X-User-Id: 2' localhost:8080/api/projects/1   # 403

# 4. 동시 수정 충돌 — 같은 version을 두 번 보내면 두 번째가 거절된다
curl -s -X PATCH -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
     -d '{"status":"DONE","version":0}' localhost:8080/api/projects/1/tasks/1   # 200, version 1
curl -s -X PATCH -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
     -d '{"status":"TODO","version":0}' localhost:8080/api/projects/1/tasks/1   # 409

# 5. 검색 · 상태 필터 · 페이징
curl -s -G -H 'X-User-Id: 1' --data-urlencode 'keyword=로그인' \
     -d 'status=TODO' -d 'page=0' -d 'size=2' \
     localhost:8080/api/projects/1/tasks
```

5번의 한글 keyword는 URL 인코딩이 필요합니다. Tomcat이 쿼리 문자열의 비-ASCII 원문을 400으로 거절하기 때문이고, 브라우저와 Swagger UI는 자동으로 인코딩하므로 curl에서만 발생합니다.

### 지금 되는 것

- 내가 속한 프로젝트만 조회 — 비멤버에게는 존재조차 숨김(404)
- 멤버지만 역할이 부족하면 403
- 마지막 OWNER 강등·제거 차단(409)
- 작업 동시 수정 충돌 감지 — PATCH·DELETE 모두 409
- 작업 검색 · 상태 필터 · 페이징

다음: `./gradlew test` 실행 (약 10초).

---

## REST API 명세 요약

### 사용자


| 메서드  | 경로                | 권한  | 응답                             |
| ---- | ----------------- | --- | ------------------------------ |
| POST | `/api/users`      | —   | 201 + `Location`. 이메일 중복 시 409 |
| GET  | `/api/users/{id}` | —   | 200 / 404                      |


`X-User-Id` 헤더는 위 두 엔드포인트를 제외한 모든 경로에서 필수입니다. 없거나 숫자가 아니면 400.

### 프로젝트


| 메서드    | 경로                          | 권한           | 비고                                    |
| ------ | --------------------------- | ------------ | ------------------------------------- |
| POST   | `/api/projects`             | 누구나          | 생성자가 OWNER 멤버로 같은 트랜잭션에 등록됨           |
| GET    | `/api/projects`             | —            | **내가 속한 프로젝트만**. `?page=&size=&sort=` |
| GET    | `/api/projects/{projectId}` | 멤버           | 비멤버 404                               |
| PATCH  | `/api/projects/{projectId}` | OWNER, ADMIN | 부분 수정. `null` = 미변경                   |
| DELETE | `/api/projects/{projectId}` | OWNER        | 204. 멤버십 행을 같은 트랜잭션에서 먼저 삭제           |


### 멤버


| 메서드    | 경로                                           | 권한                                           | 비고                                            |
| ------ | -------------------------------------------- | -------------------------------------------- | --------------------------------------------- |
| GET    | `/api/projects/{projectId}/members`          | 멤버                                           | `List` (상한이 작아 페이징 없음)                        |
| POST   | `/api/projects/{projectId}/members`          | OWNER, ADMIN — **OWNER로 추가는 OWNER만**         | 중복 추가 409                                     |
| PATCH  | `/api/projects/{projectId}/members/{userId}` | OWNER, ADMIN — **OWNER 대상·OWNER 부여는 OWNER만** | 마지막 OWNER 강등 409. 위 제한 위반은 403                |
| DELETE | `/api/projects/{projectId}/members/{userId}` | OWNER, ADMIN — **OWNER 대상은 OWNER만**          | 마지막 OWNER 제거 409. 제거된 멤버가 담당하던 작업은 담당자가 비워집니다 |


### 작업


| 메서드    | 경로                                         | 권한                   | 비고                                           |
| ------ | ------------------------------------------ | -------------------- | -------------------------------------------- |
| POST   | `/api/projects/{projectId}/tasks`          | 멤버                   | 상태는 `TODO`·`IN_PROGRESS`·`DONE`. 생략 시 `TODO` |
| GET    | `/api/projects/{projectId}/tasks`          | 멤버                   | `?keyword=&status=&page=&size=&sort=`        |
| GET    | `/api/projects/{projectId}/tasks/{taskId}` | 멤버                   | 타 프로젝트 작업은 404                               |
| PATCH  | `/api/projects/{projectId}/tasks/{taskId}` | 담당자 본인, OWNER, ADMIN | 본문 **`version` 필수**. 불일치 409. 상태 전이 제약 없음    |
| DELETE | `/api/projects/{projectId}/tasks/{taskId}` | 담당자 본인, OWNER, ADMIN | **`?version=` 필수**. 불일치 409, 누락 400, 성공 204  |


### 요청 · 응답 예시

**작업 생성** — `POST /api/projects/1/tasks`, `X-User-Id: 1`

```json
{ "title": "검색 결과 정렬 추가", "description": "최신순 기본", "status": "TODO", "assigneeId": 2 }
```

```
201 Created
Location: /api/projects/1/tasks/100
```

```json
{
  "id": 100, "title": "검색 결과 정렬 추가", "description": "최신순 기본",
  "status": "TODO", "assigneeId": 2, "version": 0,
  "createdAt": "2026-08-25T14:02:11.331", "updatedAt": "2026-08-25T14:02:11.331"
}
```

**목록 조회** — Spring `Page` 구조 그대로 나갑니다.

```json
{ "content": [ ... ], "totalElements": 4, "totalPages": 2, "number": 0, "size": 2 }
```

**충돌** — 오류는 전부 RFC 9457 `ProblemDetail`입니다.

```
409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "Task was modified by another user",
  "instance": "/api/projects/1/tasks/1"
}
```

**검증 실패** — 어느 필드가 왜 틀렸는지 `errors` 확장 필드로 내려줍니다.

```json
{
  "type": "about:blank", "title": "Bad Request", "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/users",
  "errors": { "email": "올바른 형식의 이메일 주소여야 합니다" }
}
```

### 오류 코드


| 상태  | 언제                                              |
| --- | ----------------------------------------------- |
| 400 | 요청 본문 검증 실패 / `X-User-Id` 부재·숫자 아님                |
| 403 | 프로젝트 멤버지만 요구 역할에 못 미침                           |
| 404 | 리소스 없음 **또는 프로젝트 비멤버** ([설계 결정](#접근-거부-코드--비멤버-404-멤버지만-역할-부족-403)) |
| 409 | 이메일 중복 · 멤버 중복 · 마지막 OWNER 불변식 위반 · 작업 동시 수정 충돌 |


---

## 주요 설계 결정과 그 이유

### 작업 동시 수정 충돌 — 애플리케이션 레벨 version 비교 + `@Version`

**결론.** 응답에 `version`을 실어 보내고, 클라이언트가 수정 요청에 그대로 되돌려보냅니다. 서비스가 명시적으로 비교해 불일치하면 **409**입니다. 이 비교는 **수정(PATCH)과 삭제(DELETE) 둘 다**에 걸립니다 — 삭제도 분실 갱신이기 때문입니다. 남이 이미 고친 작업을 낡은 화면에서 그대로 지우면 그 수정이 통째로 사라집니다. PATCH는 본문 `version`, DELETE는 본문이 없으므로 `?version=` 쿼리 파라미터입니다.

```java
if (!Objects.equals(request.version(), task.getVersion())) {
    throw new ConflictException("Task was modified by another user");
}
```

**왜.** 실제 시나리오는 *트랜잭션 밖*의 충돌입니다. A가 화면을 열어 작업을 읽고(트랜잭션 1 종료), B가 수정하고, 한참 뒤 A가 저장합니다(트랜잭션 2 시작). 두 트랜잭션은 시간적으로 겹치지 않습니다.
**`@Version` 애노테이션만으로는 이 상황이 잡히지 않습니다.** 한 요청 안에서 `load → modify → save` 하면 Hibernate는 *방금 읽은* version과 비교하므로, 클라이언트가 낡은 version을 보내도 통과합니다. 그래서 두 겹으로 뒀습니다 — **애플리케이션 레벨 비교**가 트랜잭션 밖 충돌(= 과제가 말한 상황)을 잡는 본체이고, **`@Version` 컬럼**이 진짜로 겹친 동시 트랜잭션을 DB 레벨에서 잡습니다(`ObjectOptimisticLockingFailureException` → 409).
검사 순서도 고정했습니다: **권한(403) → version 비교(409) → 수정**. 순서가 바뀌면 권한 없는 사용자가 403/409 차이로 "다른 사람이 이미 고쳤는지"를 알아낼 수 있습니다.


| 기각한 대안                         | 이유                                                                                                                                                                            |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **ETag + `If-Match`**          | HTTP 표준이고 REST 성숙도는 더 높지만, 내부적으로 결국 version 비교가 필요하므로 위 선택의 **상위집합**입니다 — 헤더 변환 레이어만 순수하게 추가됩니다. 과제에 조건부 요청·캐싱 요구가 없고, Swagger UI에서 리뷰어가 `If-Match`를 손으로 입력해야 해 확인 경험도 나빠집니다. |
| **비관적 락(`PESSIMISTIC_WRITE`)** | 트레이드오프 문제가 아니라 **적용 구간이 아닙니다.** 락은 트랜잭션 내에서만 유효한데 이 충돌은 트랜잭션 밖에서 발생합니다. 요구사항을 해결하지 못하면서 처리량만 떨어뜨립니다.                                                                          |
| **Last-write-wins 유지 + 문서 서술** | 과제 문장이 "결정하고 구현하세요"입니다. 구현물이 0줄이면 미구현으로 읽힙니다                                                                                                                                  |


**감수한 비용.** 클라이언트가 `version`을 들고 다녀야 하고, 응답 DTO에 `version`이 노출됩니다. 충돌 시 재시도(다시 읽고 다시 보내기)는 클라이언트 몫입니다.

### 접근 거부 코드 — 비멤버 404, 멤버지만 역할 부족 403

**결론.** 취향이 아니라 명세가 강제한 결론입니다. 규칙은 `ProjectAccessGuard` 한 곳에 있고, 전 도메인이 이 게이트를 통과합니다.

```java
ProjectMember requireMember(Long projectId, Long userId);                       // 실패 → 404
ProjectMember requireRole(Long projectId, Long userId, ProjectRole... allowed); // 실패 → 403
```

**왜.** 과제가 "프로젝트에 속하지 않은 사용자는 **조회를 포함해** 아무것도 할 수 없다"고 했는데, 403을 주면 "그 id의 프로젝트는 존재한다"가 노출되어 id를 순서대로 요청하는 것만으로 프로젝트 존재 여부를 전수 조사할 수 있습니다.
반대로 이미 경계 안에 있는 멤버에게 404를 주면 **"내 프로젝트가 사라졌나?"**로 오해합니다. 여기선 403이 정확합니다.
`requireRole`은 내부에서 반드시 `requireMember`를 먼저 호출합니다 — 순서가 뒤집히면 비멤버가 403을 받아 존재 은닉이 깨집니다. 이 순서를 `ProjectAccessGuardTest`로 잠갔습니다.


| 기각한 대안 | 이유                                                       |
| ------ | -------------------------------------------------------- |
| 전부 404 | 과도한 은닉. 멤버인데 권한만 부족한 경우까지 404면 혼란스럽고 얻는 실익이 없습니다         |
| 전부 403 | 가장 직관적이지만 비멤버에게 리소스 존재를 노출합니다. 과제가 강조한 격리 요구와 정면으로 충돌합니다 |


**감수한 비용.** 설명할 규칙이 하나 늘었습니다. 디버깅 시 구분은 `ProblemDetail`의 `detail` 메시지로 보완합니다.

### 요청자 식별 — `X-User-Id` 헤더 + `@CurrentUser` ArgumentResolver

**결론.** 문자 그대로의 쿼리 파라미터 대신 **헤더**를 골랐습니다. 이유는 하나입니다 — 인증을 실제 구현(JWT/세션)으로 교체할 때 **변경 지점이 resolver 한 곳**이기 때문입니다.

```java
@GetMapping("/api/projects")
Page<ProjectResponse> findMine(@CurrentUser Long userId, Pageable pageable) { ... }
```

**왜.** 과제 문구는 "요청자의 식별자는 API 파라미터로 전달된다고 가정합니다"입니다. 인증 정보는 리소스 파라미터와 다른 공간에 있어야 한다고 봤습니다.
부수 효과로 resolver는 **사용자 존재를 검사하지 않습니다.** 없는 id는 `requireMember`에서 멤버십 부재로 자연히 404가 되고, 덕분에 resolver에 `UserRepository` 의존이 붙지 않아 단위 테스트가 순수합니다.
다만 `POST /api/projects`는 이 게이트가 덮지 못하는 유일한 진입점이라, 거기서만 사용자 존재를 명시적으로 확인합니다 — 없으면 FK 위반 500이 아니라 404입니다.


| 기각한 대안                   | 이유                                                                                                                                          |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| 쿼리 파라미터 `?requesterId=1` | 과제 문구에 가장 충실하지만 인증 정보가 필터와 같은 공간에 섞입니다 — `GET /tasks?requesterId=1&status=DONE`에서 무엇이 인증이고 무엇이 필터인지 구분되지 않습니다. POST/PATCH에서 바디냐 쿼리냐도 애매해집니다 |
| 헤더 + 쿼리 폴백 둘 다           | 인증 진입점이 2개가 되어 우선순위 규칙과 분기가 늘어납니다. 명세에 없는 유연성입니다                                                                                            |


**감수한 비용.** "파라미터"의 문자적 해석과 다릅니다. Swagger 입력이 번거로워지는 문제는 `OperationCustomizer` 약 10줄로 `X-User-Id`를 전 오퍼레이션에 자동 주입해 해소했습니다.

### 응답 형식 — 성공은 bare DTO, 오류는 RFC 9457

**결론.** 공통 envelope(`ApiResponse<T>`)을 쓰지 않았습니다. `spring.mvc.problemdetails.enabled: true` 한 줄로 끝나고, Spring Boot 3 내장이라 추가 의존성이 0입니다.

**왜.** HTTP 상태 코드가 이미 성공/실패를 표현하는데 바디의 `"success": true`는 같은 정보의 이중 표현입니다. 오류만 RFC 9457로 두고 성공을 envelope으로 감싸면 응답 형식이 오히려 두 종류가 되어 일관성이 깨집니다.
목록은 Spring `Page`가 그대로 나가 페이징 코드가 0줄이고, springdoc 스키마도 깨끗합니다. 제네릭 래핑은 `ApiResponseProjectResponse` 같은 스키마 이름을 만들어냅니다.

**감수한 비용.** envelope의 실익인 traceId 같은 공통 메타를 실을 자리가 없습니다. 이 규모에서는 요구가 없습니다.

### 패키지 구조 — 도메인형

```
com.example.collab
├── common/    BaseTimeEntity, @CurrentUser + Resolver, GlobalExceptionHandler, exception/, OpenApiConfig
├── user/      User, Repository, Service, Controller, dto/
├── project/   Project, ProjectMember, ProjectRole, ProjectAccessGuard, Service×2, Controller×2, dto/
└── task/      Task, TaskStatus, Repository, Service, Controller, dto/
```

**왜.** 이 과제의 난이도 핵심은 권한 규칙인데, 그게 전부 `project/` 한 폴더에 응집됩니다. 계층형(`controller/ service/ repository/`)이면 `ProjectService`·`TaskService`·`ProjectAccessGuard`가 각각 다른 폴더로 흩어져, 권한 규칙 한 덩어리를 읽으려면 폴더 4개를 오가며 읽어야 합니다. 도메인이 3개뿐이라 폴더 폭발도 없습니다.
계층 간 책임 분리(컨트롤러에 로직 없음, 엔티티 직접 노출 없음)는 그대로 지킵니다 — 폴더 배치와 별개 문제입니다.

### 작업 목록 검색·필터·페이징 — 단일 `@Query`

```java
@Query("""
    SELECT t FROM Task t
    WHERE t.project.id = :projectId
      AND (:keyword IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:status  IS NULL OR t.status = :status)
    """)
Page<Task> search(Long projectId, String keyword, TaskStatus status, Pageable pageable);
```

**왜.** 프로젝트 경계(`t.project.id = :projectId`)가 **쿼리 안에** 있습니다. 애플리케이션에서 걸러내는 방식과 달리 타 프로젝트 데이터 혼입이 구조적으로 불가능합니다. 목록뿐 아니라 단건 조회도 `findByIdAndProjectId`로 경계를 함께 겁니다.
빈 문자열 `?keyword=`는 서비스에서 `null`로 정규화합니다 — 그대로 두면 `LIKE '%%'`가 되어 모든 작업이 통과해 필터가 적용되지 않습니다.

**감수한 비용.** 조건이 6~7개로 늘면 읽기 어려워집니다. 그 시점이 QueryDSL 도입이 정당해지는 시점이고, 이 과제 범위에서는 오지 않습니다. QueryDSL을 왜 지금 넣지 않았는지는 [쓰지 않기로 한 기술](#쓰지-않기로-한-기술)에 정리했습니다.

### 그 밖의 판단 — 권한 모델 관련 5건


| 항목            | 결정                  | 근거                                                                                                                            |
| ------------- | ------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| 이메일 중복        | 409                 | UNIQUE 위반은 "리소스 현재 상태와의 충돌". `OWNER 최소 1명` 위반과 같은 축으로 코드가 통일됩니다                                                               |
| 작업 담당자        | **프로젝트 멤버여야 함**     | 비멤버 담당자는 '담당자 본인' 수정 권한을 가지면서도 존재 은닉 때문에 그 작업을 조회하면 404가 됩니다 — 권한 모델이 스스로 모순됩니다                                               |
| OWNER 계층 변경   | **OWNER만 가능** (403) | ADMIN이 자신을 OWNER로 올릴 수 있으면 원래 OWNER를 제거해 OWNER 전용 권한(프로젝트 삭제)까지 도달합니다 — 권한 상승입니다                                              |
| 멤버 제거 시 담당 작업 | 담당자를 `null`로 비움     | 위 '작업 담당자' 불변식이 제거 경로에서도 유지되어야 합니다. 벌크 UPDATE 대신 엔티티를 로드해 dirty checking으로 처리합니다 — 벌크는 `@Version`을 올리지 않아 낡은 version이 계속 통합니다 |
| 삭제            | Hard delete         | Soft delete 요구가 없습니다. 도입하면 모든 조회에 필터 조건이 붙는 비용만 발생합니다                                                                         |




### 그 밖의 판단 — 구조 관련 3건

| 항목      | 결정                                | 근거                                    |
| ------- | --------------------------------- | ------------------------------------- |
| 프로젝트 삭제 | 멤버십 행을 같은 트랜잭션에서 명시적으로 먼저 삭제      | cascade 대신 명시 삭제 — 삭제 경로가 코드에 드러납니다   |
| 작업 URI  | `/api/projects/{id}/tasks/...` 중첩 | 프로젝트 경계가 URI에 드러나 권한 검사 지점이 명시적이 됩니다  |
| 멤버 목록   | `List` (페이징 없음)                   | 한 프로젝트의 멤버 수는 상한이 작습니다. 작업 목록만 `Page` |


---

## 사용한 기술과 선택 이유


| 기술                                    | 이유                                                                                                 |
| ------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Spring Boot 3.3.13                    | 과제 필수 스택(3.3.x). 3.3 계열 최신 패치                                                                      |
| Gradle toolchain 17 + foojay-resolver | 채점 환경의 JDK를 알 수 없습니다. toolchain이 JDK 17을 자동 조달해 **"클론 후 `./gradlew bootRun` 한 번"** 요구를 어디서든 만족시킵니다 |
| springdoc-openapi 2.6.0               | Boot 3.3 대응 버전(2.7+는 Boot 3.4/3.5용이라 불일치). `OperationCustomizer`로 `X-User-Id`를 전 오퍼레이션에 자동 주입      |
| Spring Data JPA                       | 과제 필수. `Page`·`Pageable`이 페이징을 그대로 처리                                                              |
| H2 인메모리 + `data.sql` · Lombok         | 둘 다 과제 필수·사실상 표준. `data.sql`은 체크리스트의 "초기 데이터를 넣어두면 좋습니다"에 대응하고, Lombok은 보일러플레이트를 제거합니다             |


### 쓰지 않기로 한 기술


| 기술                     | 이유                                                                                                            |
| ---------------------- | ------------------------------------------------------------------------------------------------------------- |
| **QueryDSL**           | annotation processor 설정 + Q타입 생성 + 빌드 경로 관리가 붙어 "클론 후 `bootRun` 한 번" 요구에 실패 리스크를 더합니다. 얻는 것은 조건 3개짜리 쿼리 하나입니다 |
| **MapStruct**          | DTO가 10개 미만입니다. `TaskResponse.from(task)` static factory가 더 짧고 빌드 설정이 0입니다                                    |
| **Flyway / Liquibase** | H2 인메모리 + `ddl-auto: create`. 마이그레이션 대상 자체가 없습니다                                                              |
| **Spring Security**    | 인증이 구현 대상이 아닙니다. 도입하면 `permitAll` 설정만 늘고 권한 로직은 여전히 서비스 계층에 남습니다                                              |
| **Docker / CI**        | 요구가 "클론 후 `./gradlew bootRun` 한 번"입니다. 추가 레이어는 실행 경로만 늘립니다                                                    |


**Redis · Kafka · MongoDB** — 캐싱·이벤트·문서 저장 요구가 명세에 없습니다.

기각 기준은 "복잡해서"가 아니라 **어느 요구를 위협하는가**입니다.

### 테스트 (24개, 약 10초)


| 파일                                | 검증                                                                                                                                    |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `ProjectAccessGuardTest`          | 비멤버 404 / 역할 부족 403 / **비멤버가 역할 검사를 거쳐도 403이 아니라 404** (검사 순서)                                                                        |
| `ProjectMemberServiceTest`        | 마지막 OWNER 강등·제거 차단, OWNER 2명이면 강등 허용, 중복 멤버 차단, **ADMIN의 자기 승격·OWNER 강등 차단**, **멤버 제거 시 담당 작업의 담당자 해제**                               |
| `TaskConcurrencyTest`             | 같은 version으로 2회 PATCH → 200 다음 409, **그리고 앞선 변경이 덮이지 않았음**. 낡은 version DELETE → 409 → 맞는 version → 204 → 404. version 누락 DELETE → 400 |
| `TaskRepositoryTest`              | keyword/status 조합, 페이징 경계, 타 프로젝트 작업 미포함                                                                                              |
| `CurrentUserArgumentResolverTest` | 헤더 정상 / 부재 / 숫자 아님                                                                                                                      |


커버리지 숫자가 아니라 **재량으로 채운 지점**에 배치했습니다. 

`TaskConcurrencyTest`에는 의도적으로 테스트 레벨 `@Transactional`을 붙이지 않았습니다. 붙이면 두 요청이 한 영속성 컨텍스트를 공유해 **트랜잭션 밖 충돌**이 재현되지 않고, `@Version`만으로도 통과하는 상황이 되어 아무것도 증명하지 못합니다.

---

## 여러 회사가 함께 쓰고 데이터가 완전히 분리되어야 한다면

### 격리 모델 선택


| 모델                          | 격리 강도              | 운영 비용                     | 판단            |
| --------------------------- | ------------------ | ------------------------- | ------------- |
| DB per tenant               | 최상 (물리 분리)         | 최상 — 테넌트마다 인스턴스·백업·마이그레이션 | 이 규모에서는 과함    |
| Schema per tenant           | 강 (논리 분리)          | 중 — 스키마별 마이그레이션           | 규제 요구가 생기면 후보 |
| **공유 스키마 + `tenant_id` 컬럼** | 중 — 애플리케이션이 경계를 보장 | 최소                        | **채택**        |


이 서비스는 이미 `project_members`로 애플리케이션 레벨 경계를 강제하고 있기 때문에 같은 메커니즘을 한 단계 위(`tenant_id`)로 올리는 것이 가장 작은 변경입니다.

### 구체적 변경 지점

1. **진입** — `CurrentUserArgumentResolver`와 같은 자리에서 `X-Tenant-Id`를 해석합니다. 인증 도입 시엔 토큰 클레임에서 추출합니다. 인터셉터/필터가 `TenantContext`(ThreadLocal)에 저장하고 **요청 종료 시 반드시 clear** — 스레드 재사용으로 이전 요청의 테넌트가 남으면 그대로 유출입니다.
2. **영속성** — `BaseTimeEntity`에 `tenantId`를 추가하고 Hibernate `@TenantId`(또는 `@Filter` + `CurrentTenantIdentifierResolver`)로 모든 조회·저장에 자동 적용합니다. **개별 쿼리에 조건을 손으로 붙이지 않습니다** — 한 곳이라도 빠지면 격리가 깨지기 때문입니다.
3. **제약** — `users.email`의 UNIQUE를 `(tenant_id, email)`로 바꾸고, 모든 UNIQUE·FK 제약을 테넌트 스코프로 재검토합니다.
4. **오류 정책** — **지금의 404/403 규칙이 그대로 확장됩니다.** 크로스 테넌트 접근은 404(존재 은닉), 테넌트 내 권한 위반은 403. 새 규칙을 만들 필요가 없습니다.
5. **검증** — 테넌트 A 컨텍스트에서 테넌트 B 리소스에 접근하면 404가 나오는 통합 테스트를 회귀 방지선으로 둡니다.

**그 다음 2건.**

6. **인덱스** — 조회 인덱스 선두에 `tenant_id`를 둡니다 (`(tenant_id, project_id, status)`)
7. **경계 밖 코드** — 배치·스케줄러·캐시 키에 `tenantId`를 반드시 포함시킵니다. 캐시 키 누락은 테넌트 간 데이터 유출로 직결됩니다

규제 요구가 생기면 schema-per-tenant로 승격하는 경로도 열려 있습니다.

---

## 구현하지 못한 부분과, 시간이 더 있었다면


| 항목                | 현재 상태                    | 더 있었다면                                                                                                                                                                     |
| ----------------- | ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **작업 담당자 해제**     | 미지원 — 담당자는 **교체만** 가능합니다 | PATCH에서 `null` = 미변경 규칙을 도메인 전체에서 일관되게 유지하려고 v1에서 뺐습니다. 해제를 지원하려면 "값 없음"과 "명시적 null"을 구분해야 해서 `JsonNullable` 같은 래퍼나 별도 엔드포인트(`DELETE .../assignee`)가 필요합니다. 후자가 이 규모에 맞습니다 |
| **프론트엔드**         | 미구현 (선택 항목)              | 작업 목록 화면 하나 — 검색·상태 필터·페이징과 409 충돌 시 "다시 읽어오기" 흐름까지가 이 API의 특성이 드러나는 최소 단위입니다                                                                                              |
| **인증**            | 구현 대상 아님                 | `CurrentUserArgumentResolver` 한 곳만 토큰 파싱으로 교체하면 됩니다. 컨트롤러 시그니처는 그대로입니다                                                                                                     |
| **컨트롤러 슬라이스 테스트** | 미작성                      | 로직이 서비스 계층에 있어 `@WebMvcTest`의 검증 가치가 낮다고 판단해 후순위로 뒀습니다. 시간이 있었다면 요청 검증·직렬화 경계만 얇게 덮었을 것입니다                                                                                 |
| **작업 상태 전이 규칙**   | 없음 (임의 전이 허용)            | `DONE → TODO`를 막을지 같은 규칙은 요구가 없어 두지 않았습니다. 필요해지면 `TaskStatus`에 전이 테이블을 두는 것이 가장 작은 변경입니다                                                                                   |




### 의도적으로 정하지 않은 것

| 항목         | 현재 상태              | 더 있었다면                                                             |
| ---------- | ------------------ | ------------------------------------------------------------------ |
| **정렬 기본값** | `?sort=` 파라미터로만 지정 | 작업 목록의 기본 정렬(예: 최신순)을 정하려면 "무엇이 자연스러운 순서인가"가 제품 결정이라 임의로 정하지 않았습니다 |


---

다음: `./gradlew test` 실행 (약 10초). 통과하면 "권한 규칙을 한 번에 확인하는 시나리오"의 curl 5개를 순서대로 실행하면 됩니다.