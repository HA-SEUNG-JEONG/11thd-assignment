# 구현 진행 기록 (Implementation Log)

> 작성일 2026-08-25 · 최종 갱신 2026-08-26 · 마감 2026-08-26 23:59
> 단계별 실행 결과·게이트 검증·미해결 항목의 기록. 설계 근거는 `design-decisions.md`, 공백 판단 규칙은 `spec-gap-policy.md`.
> 제출용 문서는 단계 6의 README. 이 문서는 단계 11에서 **커밋 대상으로 전환**됐다 — 어떻게 걸어왔는지가 결과물만큼 읽을 값이 있다고 봤다. `spec-gap-policy.md`·`interview-prep.md`는 계속 `.gitignore`에 남는다(채점축 공략 메모·면접 답변 대본이라 제출물에 섞일 성격이 아니다).

---

## 진행 현황

| 단계 | 상태 | 커밋 |
|---|---|---|
| 0 — 메모리 저장 | 완료 | — (저장소 밖) |
| 1 — 부트스트랩 | 완료 | `4a118f5` |
| 2 — 공통 인프라 + 사용자 도메인 | 완료 | `a25512b` |
| 3 — 프로젝트·멤버·권한 게이트 | 완료 | `020a5e9` |
| 4 — 작업 도메인 + 검색·필터·페이징 | 완료 | `057fb18` |
| 5 — 동시 수정 충돌 + 문서·시드 | 완료 | `8936c10` · `958b722` · `2f1ec81` |
| 6 — 테스트 + README | 완료 | `9618321` · `d579d56` (보강 `cdb8f7b`) |
| 7 — 설계 문서 스캔성 개선 | 완료 | `9b02205` |
| 8 — 제출 전 결함 3건 수정 | 완료 | `f88c952` · `862f05d` · `5b4e064` · `87a42d8` |
| 9 — README 편집 규칙 적용 | 완료 | `ed0ae4c`…`b81fcb3` (후속 `b96d172`…`4180e2c`) |
| 10 — 반복 주석 6곳 제거 | 완료 | `44ec76e` (곁다리 `26c02dc`) |
| 11 — 내부 노트 stale 정정 | 완료 | 이 커밋 |

---

# 단계 1 — 부트스트랩

커밋 `4a118f5` · `chore: Gradle 8.14.5 + Spring Boot 3.3.13 프로젝트 초기화` · 9파일 467줄

## 환경 제약과 대응

로컬에 **JDK 21만 설치**되어 있고 **JDK 17과 `gradle` 바이너리가 둘 다 없었다.** 채점자 환경도 미상이다.

| 제약 | 대응 |
|---|---|
| JDK 17 없음 | Gradle toolchain `languageVersion 17` + foojay-resolver 0.10.0. 빌드 시 JDK 17을 자동 조달 |
| `gradle` 바이너리 없음 → `gradle wrapper` 실행 불가 | wrapper 파일(`gradlew`, `gradle/wrapper/*`)을 외부에서 조달하고 `distributionUrl`을 8.14.5로 지정 |

`gradle/wrapper/gradle-wrapper.jar`(43,764 B)를 **커밋에 포함**했다. 빠지면 클론 후 `./gradlew` 실행이 불가능해져 과제 요구("클론 후 `./gradlew bootRun` 한 번")가 바로 깨진다.

## 확정 버전

| 항목 | 버전 | 선정 근거 |
|---|---|---|
| Spring Boot | 3.3.13 | 3.3.x 최신 패치. start.spring.io가 3.3을 더 이상 제공하지 않아 `build.gradle` 수기 작성 |
| springdoc-openapi-starter-webmvc-ui | 2.6.0 | Boot 3.3 대응 버전. 2.7+/2.8은 Boot 3.4/3.5용이라 불일치 |
| Gradle | 8.14.5 | 8.x 최신 |
| foojay-resolver-convention | 0.10.0 | Gradle 8.x 호환 (1.0.0은 Gradle 9 계열) |

의존성: `web`, `data-jpa`, `validation`, `h2`(runtimeOnly), `springdoc`, `lombok`, `test`.

## `application.yml` 핵심 설정

| 설정 | 이유 |
|---|---|
| `spring.mvc.problemdetails.enabled: true` | RFC 9457 오류 형식. 추가 의존성 0 |
| `spring.jpa.defer-datasource-initialization: true` | Boot 2.5+에서 `data.sql`이 Hibernate DDL보다 먼저 실행돼 "table not found"로 기동 실패하는 함정 대응 (단계 5 시드 대비 선반영) |
| `spring.jpa.open-in-view: false` | 뷰 렌더링이 없는 REST API. 커넥션 보유 구간을 트랜잭션으로 한정 |
| `spring.data.web.pageable.max-page-size: 100` | 단계 4 페이징의 상한 |
| `springdoc.swagger-ui.path: /swagger-ui.html` | 리뷰어 검증 경로 고정 |
| H2 콘솔 활성화 | 데이터 상태 직접 확인 경로 |

## 게이트 검증

| 항목 | 결과 |
|---|---|
| `./gradlew bootRun` 기동 | 성공 |
| 컴파일 산출물 바이트코드 버전 | `major version: 61` = Java 17 → **toolchain이 JDK 17을 실제로 조달**했음을 확인 |
| `/swagger-ui.html` | HTTP 200 |

바이트코드 버전 확인이 이 게이트의 핵심이다. JDK 21로 컴파일되면 채점자 환경이 17일 때 `UnsupportedClassVersionError`가 나는데, 기동 성공만으로는 구분되지 않는다.

## `.gitignore`

`build/`, `.gradle/`, `bin/`, `.idea/`, `*.iml`, `.vscode/`, `*.log`, `.DS_Store`, `.omc/` 에 더해 **내부 설계 노트 3종을 같은 커밋에 함께 제외**했다.

```
docs/design-decisions.md
docs/spec-gap-policy.md
docs/interview-prep.md
```

---

# 단계 2 — 공통 인프라 + 사용자 도메인

커밋 `a25512b` · `feat: 사용자 도메인 및 요청자 식별 ArgumentResolver 구현` · 15파일 370줄

## 만든 것

```
common/
  CurrentUser.java                    @Target(PARAMETER) 마커 애노테이션
  CurrentUserArgumentResolver.java    X-User-Id 파싱
  WebMvcConfig.java                   resolver 등록
  GlobalExceptionHandler.java         전 오류 → ProblemDetail
  exception/  NotFoundException · ForbiddenException
              ConflictException · MissingUserIdException
user/
  User.java · UserRepository.java · UserService.java · UserController.java
  dto/UserCreateRequest.java · dto/UserResponse.java
test/
  common/CurrentUserArgumentResolverTest.java
```

## 실행 중 내린 판단

### 1. `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속한다

**문제.** `spring.mvc.problemdetails.enabled: true`이면 Spring Boot가 `ProblemDetailsExceptionHandler`를 자동 등록하고, 이것이 `MethodArgumentNotValidException`을 이미 처리한다. 독립 `@RestControllerAdvice`에 `@ExceptionHandler(MethodArgumentNotValidException.class)`를 선언하면 **어느 쪽이 이길지가 등록 순서에 달린다.** 지면 `errors` 확장 필드가 조용히 사라진다 — 예외도, 로그도 없이 응답에서만 빠진다.

**대응.** Boot의 자동 등록은 `ResponseEntityExceptionHandler` 타입 빈이 없을 때만 이루어진다. 상속하면 Boot 쪽이 깨끗하게 물러나므로 경합 자체가 사라진다. `handleMethodArgumentNotValid`를 오버라이드해 `errors`를 추가한다.

```java
ProblemDetail body = e.getBody();
body.setDetail("Request validation failed");
body.setProperty("errors", errors);          // 필드명 → 메시지
return handleExceptionInternal(e, body, headers, status, request);
```

커스텀 예외는 같은 클래스 안에서 `@ExceptionHandler`로 처리한다. `ProblemDetail`을 그대로 반환하면 상태코드와 `instance`(요청 경로)가 Spring에서 자동으로 채워진다 — 아래 curl 응답이 그 증거다.

### 2. 이메일 중복 → 409

`users.email` UNIQUE 위반은 "리소스의 현재 상태와의 충돌"이다. 단계 3의 `OWNER 최소 1명` 불변식 위반과 같은 축에 놓여 코드가 하나로 통일된다.

`UserService`의 `existsByEmail` 사전 검사로 409를 낸다. `DataIntegrityViolationException` 매핑은 **넣지 않았다.** 동시 요청이 검사와 저장 사이를 파고드는 레이스 윈도가 이론상 존재하지만, 이 규모에서 그 경로를 막는 비용이 이득보다 크다. 확정된 오류 매핑표를 늘리지 않는다.

### 3. resolver는 사용자 존재를 검사하지 않는다

없는 ID는 단계 3의 `requireMember`에서 멤버십 부재로 자연히 404가 된다. 여기서 검사하면
- resolver에 `UserRepository` 의존이 붙어 단위 테스트가 스프링 컨텍스트를 요구하게 되고
- 인증을 JWT로 교체할 때 이 의존이 그대로 짐이 된다

### 4. `MissingUserIdException`이 필요한 이유

`MissingRequestHeaderException`은 `@RequestHeader` 파라미터에서만 발생한다. `@CurrentUser`는 커스텀 resolver라 헤더가 없어도 프레임워크가 아무 예외도 만들어주지 않는다. **자체 예외를 던져야** 400이 나간다. 헤더 부재와 비수치 값 두 경로 모두 이 예외로 모은다.

## 확정된 오류 매핑

| 예외 | 상태 | 최초 사용 단계 |
|---|---|---|
| `NotFoundException` | 404 | 2 |
| `ForbiddenException` | 403 | 3 |
| `ConflictException` | 409 | 2 |
| `ObjectOptimisticLockingFailureException` | 409 | 5 |
| `MethodArgumentNotValidException` | 400 + `errors` | 2 |
| `MissingUserIdException` | 400 | 3 |

`ForbiddenException`과 낙관적 락 핸들러는 단계 3·5까지 **미사용 코드다.** 의도적이다 — 오류 매핑표를 한 파일에 완결시키면 리뷰어가 오류 정책 전체를 파일 하나로 읽는다.

## 미룬 것

`BaseTimeEntity`를 **단계 3으로 미뤘다.** 확정 스키마상 `users`에는 `created_at`이 없어 단계 2에서 쓸 곳이 0이다. 첫 사용처인 `Project`와 같은 커밋에 넣는다.

## 게이트 검증

`./gradlew build` BUILD SUCCESSFUL · `CurrentUserArgumentResolverTest` tests=3 failures=0

| 시나리오 | 기대 | 실제 |
|---|---|---|
| `POST /api/users` | 201 + `Location` | 201, `Location: /api/users/1` |
| 같은 이메일 재요청 | 409 | 409 ProblemDetail |
| `name:""`, `email:"not-an-email"` | 400 + `errors` | 400, 두 필드 모두 `errors`에 포함 |
| `GET /api/users/1` | 200 | 200 |
| `GET /api/users/999` | 404 | 404 ProblemDetail |
| `/swagger-ui.html` | 200 | 200 |
| `git status` | 클린, `docs/` 미추적 | 확인 |

```json
// 409
{"type":"about:blank","title":"Conflict","status":409,
 "detail":"Email already in use: alice@example.com","instance":"/api/users"}

// 400 — errors 확장 필드가 실제로 실렸는지가 판단 1의 검증 지점
{"type":"about:blank","title":"Bad Request","status":400,
 "detail":"Request validation failed","instance":"/api/users",
 "errors":{"email":"올바른 형식의 이메일 주소여야 합니다","name":"공백일 수 없습니다"}}
```

## 테스트를 여기에 둔 이유

`CurrentUserArgumentResolverTest` 3케이스(헤더 정상 / 부재 / 비수치)가 단계 2의 유일한 테스트다.

요청자 식별 방식은 명세가 "API 파라미터로 전달된다고 가정합니다"까지만 말하고 형식을 정하지 않은, **재량으로 채운 지점**이다. `spec-gap-policy.md`의 원칙 — *명세가 준 부분은 틀리면 명세 탓, 내가 채운 부분은 틀리면 내 탓* — 이 그대로 적용된다.

단계 2에는 `@CurrentUser`를 쓰는 엔드포인트가 없어 curl로는 resolver에 도달할 수 없다. 그래서 `MockHttpServletRequest` + `ServletWebRequest`로 resolver를 직접 호출한다. `resolveArgument`가 `MethodParameter`를 사용하지 않으므로 합성 컨트롤러 메서드를 만들 필요가 없다.

## 미검증 항목 — 단계 3에서 반드시 확인

**`WebMvcConfig.addArgumentResolvers` 등록이 end-to-end로 확인되지 않았다.**

단위 테스트는 resolver를 `new`로 직접 만들어 호출하므로, 등록 한 줄이 빠져 있어도 단계 2의 모든 게이트를 통과한다. 실제 실패는 단계 3에서 `@CurrentUser`를 쓰는 첫 엔드포인트가 이상한 400/500을 낼 때 드러난다.

단계 3 첫 엔드포인트 구현 직후 `curl -H 'X-User-Id: 1'`로 즉시 확인할 것.

---

## 다음 단계 (3) 진입 시 상기할 것

1. 위 미검증 항목 확인
2. `BaseTimeEntity`를 `Project`와 같은 커밋에 도입
3. `ProjectAccessGuard` 고정 순서 — `requireMember`(404) → `requireRole`(403)
4. 목록은 `WHERE project_id IN (내 멤버십)`. **애플리케이션 필터링 금지**
5. `project_members`에 `unique(project_id, user_id)`
6. 프로젝트 생성 시 생성자를 OWNER 멤버로 **같은 트랜잭션**에 등록

---

# 단계 3 — 프로젝트 · 멤버 · 권한 게이트

커밋 `020a5e9`. 파일 17개 신규(클래스 11 + DTO 6) + `CollabApplication` 수정 1건.

## 단계 2 미검증 항목 종결

`WebMvcConfig.addArgumentResolvers` 등록이 end-to-end로 확인됐다.

```
$ curl -s localhost:8080/api/projects            # X-User-Id 없이
{"type":"about:blank","title":"Bad Request","status":400,
 "detail":"X-User-Id header is required","instance":"/api/projects"}
```

`detail`이 `MissingUserIdException`의 메시지 그대로다. 등록이 빠졌다면 Spring이 `Long userId`를 모델 속성으로 바인딩하려다 전혀 다른 응답을 냈을 것이므로, 이 문자열이 등록 확인의 증거가 된다.

## 존재 은닉의 구현 조건 — 메시지까지 같아야 한다

`requireMember`는 "프로젝트가 없다"와 "요청자가 멤버가 아니다"를 **한 쿼리로 구분 없이** 처리한다. `findByProjectIdAndUserId`가 비면 둘 중 어느 쪽인지 알 수 없고, 알 필요도 없다.

상태코드만 404로 맞추고 메시지를 다르게 쓰면 은닉이 깨진다. `"Project not found: 1"` vs `"You are not a member of project 1"` 은 후자가 프로젝트의 존재를 확정해 주기 때문이다. 그래서 `ProjectService.loadProject`의 메시지도 게이트와 동일한 문자열로 맞췄다.

```
$ curl -H 'X-User-Id: 2' localhost:8080/api/projects/1   # 비멤버
404 {"detail":"Project not found: 1"}
$ curl -H 'X-User-Id: 2' localhost:8080/api/projects/999  # 진짜 없음
404 {"detail":"Project not found: 999"}
```

## 검사 순서가 권한 규칙의 일부다

`ProjectMemberService.add`의 순서: **권한 → 대상 존재 → 중복**.

중복 검사를 앞에 두면 권한 없는 사용자가 응답 코드 차이(409 vs 403)로 "누가 이 프로젝트 멤버인지"를 알아낼 수 있다. 순서를 바꾸는 것만으로 정보 노출 경로가 생기므로 이 순서는 취향이 아니라 규칙이다.

DB의 `unique(project_id, user_id)`는 경합 상황의 최종 방어선으로 남겨 두고 별도 예외 처리를 붙이지 않았다. 애플리케이션 레벨 검사가 정상 경로를 전담한다.

## 계획대로 걸린 함정 3건

| 함정 | 확인된 결과 |
|---|---|
| 없는 `X-User-Id`로 프로젝트 생성 → FK 위반 500 | `ProjectService.create`에서 `userRepository.findById` 명시 검사. `X-User-Id: 999` → **404 "User not found: 999"** |
| 프로젝트 삭제 시 `project_members` 잔존 → FK 위반 500 | `deleteByProjectId` 선행. 멤버 2명 있는 P2 삭제 → **204**, 이후 조회 404 |
| 멤버 목록의 name·email이 LAZY `user`라 N+1 | `JOIN FETCH m.user` 쿼리 1개. 응답에 두 멤버의 name·email 정상 포함 |

## 계획에 없던 결함 1건 — `updatedAt`이 수정 전 값으로 나갔다

첫 검증에서 `PATCH /api/projects/1` 응답의 `updatedAt`이 `createdAt`과 동일했다.

```
PATCH 응답  "createdAt":"...T15:27:36.465199","updatedAt":"...T15:27:36.465199"
```

원인은 `@LastModifiedDate`의 갱신 시점이다. `AuditingEntityListener`는 `@PreUpdate`에서 값을 채우고, `@PreUpdate`는 flush 시점에 실행된다. 트랜잭션 커밋 전에 `ProjectResponse.from(project)`로 스냅샷을 뜨면 아직 갱신되지 않은 값을 읽는다. DB에는 올바른 값이 들어가므로 **응답만 거짓말을 하는** 형태였고, GET으로 다시 조회하면 정상이라 놓치기 쉽다.

`saveAndFlush`로 flush를 앞당겨 해결했다.

```
PATCH 응답  "createdAt":"...T15:28:49.986772","updatedAt":"...T15:28:50.024682"
```

## `PageImpl` 직렬화 경고 — 의도적으로 남긴다

`GET /api/projects` 호출 시 다음 경고가 뜬다.

```
WARN Serializing PageImpl instances as-is is not supported, meaning that there is
no guarantee about the stability of the resulting JSON structure!
```

`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`로 없앨 수 있으나 **적용하지 않았다.** 그 설정은 페이징 메타를 `page` 객체 안으로 옮기는데, `design-decisions.md`의 응답 형식 절이 확정한 모양은 `content / totalElements / totalPages / number / size`가 최상위에 오는 현재 구조다. 경고를 없애려고 확정된 응답 계약을 바꾸는 것은 순서가 거꾸로다.

경고의 실제 의미는 "Spring Data가 이 JSON 구조의 안정성을 보장하지 않는다"이며, 이 과제 범위에서는 감수 가능한 비용이다.

## 설계 문서 내부 불일치 1건

응답 형식 절은 "목록 = `Page` 구조", `@CurrentUser` 설명의 코드 예시는 `List<ProjectResponse>`로 서로 어긋난다. **응답 형식 절을 규범으로 삼았다** — 그쪽이 결정과 근거를 명시한 절이고, 예시는 resolver 사용법을 보이기 위한 축약이다.

- `GET /api/projects` → `Page`
- `GET /api/projects/{id}/members` → `List` (멤버 수는 상한이 작아 페이징 이득이 없다)

## 명세가 정하지 않아 채운 값

| 항목 | 결정 |
|---|---|
| `DELETE` 성공 | 204 No Content |
| `PATCH` 성공 | 200 + 변경된 리소스 |
| `POST .../members` 성공 | 201 + 멤버 표현. `Location` 없음 — 멤버 단건 GET 엔드포인트가 없다 |
| 잘못된 enum 값 (`"role":"BOSS"`) | 400. `MethodArgumentNotValidException`이 아니라 `HttpMessageNotReadableException`이며, `ResponseEntityExceptionHandler` 기본 처리가 이미 ProblemDetail로 변환한다 |

## 게이트 결과

`./gradlew build` 통과. curl 시나리오 전항 통과.

| 검증 | 기대 | 실제 |
|---|---|---|
| u1 프로젝트 생성 | 201 + Location | 201 `/api/projects/1` |
| `X-User-Id` 없이 목록 | 400 | 400 |
| u2(비멤버) 단건 조회 | 404 | 404 |
| u1이 u2를 MEMBER로 추가 | 201 | 201 |
| u2 단건 조회 (멤버가 된 후) | 200 | 200 |
| u2 프로젝트 삭제 | 403 | 403 |
| 유일 OWNER를 MEMBER로 변경 | 409 | 409 |
| 중복 멤버 추가 | 409 | 409 |
| u1 목록 조회 | 내 프로젝트만 `Page` | `totalElements:1` (제외 케이스는 단계 5 시드에서 확인) |
| 없는 사용자로 생성 | 404 | 404 |

## 다음 단계 (4) 진입 시 상기할 것

1. `(:param IS NULL OR ...)` JPQL은 작성 직후 `@DataJpaTest`로 **즉시** 확인 — Hibernate 6 / H2 파라미터 타입 추론 이슈
2. 작업 수정·삭제 권한은 `담당자 본인 || OWNER || ADMIN` — `requireRole`만으로 표현되지 않으므로 서비스에서 조합
3. `TaskResponse`는 담당자를 `assigneeId`로만 노출 (프록시 초기화 금지)
4. `Task`는 `BaseTimeEntity` 상속 대상
5. 인덱스 `(project_id, status)`

---

# 단계 4 — 작업 도메인 + 검색·필터·페이징

커밋 `057fb18` `feat: 작업 도메인 및 검색·상태 필터·페이징 구현`

## 만든 것

파일 9개 (계획 8 + 테스트 1) + 기존 파일 1개 수정.

```
task/TaskStatus.java            TODO / IN_PROGRESS / DONE
task/Task.java                  BaseTimeEntity 상속. project(LAZY), assignee(LAZY, nullable)
                                @Index(name="idx_tasks_project_status", columnList="project_id, status")
task/TaskRepository.java        search(JPQL) / findByIdAndProjectId / deleteByProjectId
task/TaskService.java
task/TaskController.java        /api/projects/{projectId}/tasks
task/dto/TaskCreateRequest.java
task/dto/TaskUpdateRequest.java
task/dto/TaskResponse.java
test/task/TaskRepositoryTest.java   @DataJpaTest 6케이스
project/ProjectService.java     (수정) delete에 taskRepository.deleteByProjectId 추가
```

## 계획된 함정 4건의 실제 결과

| 함정 | 결과 |
|---|---|
| `(:param IS NULL OR ...)` 파라미터 타입 추론 | **발동하지 않음.** Spring Boot 3.3.13(Hibernate 6.5)에서 `String`·enum 둘 다 우회책 없이 동작. 쿼리 작성 직후 `@DataJpaTest` 6케이스로 확인 후 그대로 확정. 계획대로 미리 `CAST`를 넣지 않은 것이 맞았다 |
| 타 프로젝트 작업 유출 | `findByIdAndProjectId`로 차단. P2의 task 4를 `/api/projects/1/tasks/4`로 조회 → 404, PATCH도 404(403 아님) |
| 담당자 이름 노출로 인한 N+1 | `Task.getAssigneeId()`가 LAZY 프록시의 식별자만 읽는다. 프록시가 초기화되지 않아 목록에서 추가 쿼리 없음 |
| 없는 `assigneeId` | 별도 검사 없이 `requireMember`가 404로 처리. `assigneeId: 999` → 404 확인 |

## 계획 밖 결함 1건 — 프로젝트 삭제 FK 위반

단계 3에서 `project_members`를 명시 삭제했지만, 단계 4가 `tasks.project_id`라는 **두 번째 자식 테이블**을 만든다. 계획의 파일 8개 목록에 `ProjectService.delete`가 없어 그대로 두면 "프로젝트 생성 → 작업 추가 → 프로젝트 삭제"가 FK 위반 500이 된다. 단계 5·6도 이 경로를 건드리지 않고 최종 검증 시나리오에도 없다.

대응: `TaskRepository.deleteByProjectId` 추가 후 `ProjectService.delete`에서 작업 → 멤버 → 프로젝트 순으로 삭제. 새 설계 결정이 아니라 단계 3에서 이미 확정한 "명시 삭제, cascade 금지" 규칙을 이번 단계가 만든 테이블에 적용한 것. 게이트 마지막 항목으로 검증 추가.

## 단계 3에서 이월된 규칙

`updatedAt` 스냅샷 시점 — `TaskResponse`도 `updatedAt`을 노출하므로 `TaskService.update`에 동일하게 `saveAndFlush`를 쓴다. PATCH 응답의 `updatedAt`이 `createdAt`과 달라지는 것으로 확인(`15:53:01.010` → `15:53:22.298`).

## 합성 권한 구현 메모

`requireRole`을 쓰면 담당자 분기 전에 403이 먼저 터진다. 따라서 `requireMember`로 `ProjectMember`를 받아온 뒤 서비스에서 조합한다.

```java
ProjectMember member = accessGuard.requireMember(projectId, userId);  // 비멤버 404
Task task = loadTask(projectId, taskId);                             // 없거나 타 프로젝트 404
if (!member.hasAnyRole(OWNER, ADMIN) && !userId.equals(task.getAssigneeId())) {
    throw new ForbiddenException(...);                               // 403
}
```

수정·삭제가 같은 헬퍼(`loadWritableTask`)를 쓴다 — 두 경로의 검사 순서가 갈라지지 않게.

## 게이트 결과

`./gradlew build` 통과. 테스트 9개(단계 2의 3 + 이번 6) 전부 통과. curl 전항 통과.

| 검증 | 기대 | 실제 |
|---|---|---|
| u1(OWNER) 작업 3개 생성 | 201 + Location | 201, `Location: /api/projects/1/tasks/1` |
| `status` 생략 | TODO | `"status":"TODO"` |
| `?keyword=` (빈 문자열) | 필터 없음 | total 3 — 서비스에서 null 정규화 동작 |
| `?keyword=login` | 대소문자 무시 부분 일치 | total 2 (`Design login page`, `Write LOGIN docs`) |
| `?status=TODO` | total 2 | total 2 |
| `?keyword=login&status=TODO` | AND 결합, total 1 | total 1 |
| `?page=0&size=2` / `page=1` | 2건 / 1건, totalPages 2 | 일치 |
| u2(MEMBER)가 자기 담당 작업 PATCH | 200 | 200, `updatedAt` 갱신 |
| u2가 남의 담당 작업 PATCH | 403 | 403 |
| u2가 남의 담당 작업 DELETE | 403 | 403 |
| u2가 자기 담당 작업 DELETE | 204 | 204 |
| 비멤버 u3가 목록 조회 | 404 | 404 `Project not found: 1` |
| 다른 프로젝트 taskId로 단건 조회 | 404 | 404 `Task not found: 4` |
| 비멤버를 `assigneeId`로 지정 | 404 | 404 |
| 없는 `assigneeId: 999` | 404 | 404 |
| `title: "  "` | 400 + `errors` | 400 `{"title":"공백일 수 없습니다"}` |
| **작업이 남은 프로젝트 삭제(OWNER)** | 204 | 204 — FK 위반 없음 |
| 삭제 후 P2와 그 작업 | 영향 없음 | total 1 유지 |

`git status` 클린, `docs/` 미추적.

## 다음 단계 (5) 진입 시 상기할 것

1. `@Version`은 이번 단계에서 의도적으로 넣지 않았다 — `Task`·`TaskUpdateRequest` 둘 다 `version` 없음(`grep` 확인)
2. `@Version` 단독으로는 안 잡힌다. `TaskUpdateRequest.version` 명시 비교가 본체
3. `data.sql` 시드가 들어와야 단계 3에서 미룬 "내 프로젝트만 목록에" 제외 케이스를 curl로 확인할 수 있다
4. README에 기록할 v1 제약 1줄: **담당자 해제 미지원** (PATCH `null = 미변경` 규칙 유지, 교체만 가능)

---

# 단계 5 — 동시 수정 충돌 + 문서 · 시드

커밋 3개: `8936c10` 낙관적 락 / `958b722` Swagger 헤더 / `2f1ec81` 시드. push 완료.

## 만든 것

| 파일 | 비고 |
|---|---|
| `task/Task.java` | `@Version private Long version` 추가 |
| `task/dto/TaskUpdateRequest.java` | `@NotNull Long version` — 이 record에서 유일한 필수 필드 |
| `task/dto/TaskResponse.java` | `version` 노출 (계획 파일 목록에 없었음 — 아래 참조) |
| `task/TaskService.java` | `Objects.equals` 명시 비교 → `ConflictException` |
| `common/OpenApiConfig.java` | `OperationCustomizer`로 `X-User-Id` 주입 |
| `src/main/resources/data.sql` | 사용자 3 · 프로젝트 2 · 작업 5 |
| `task/TaskRepositoryTest.java` (수정) | `spring.sql.init.mode=never` |

## 검사 순서 (409가 들어간 자리)

멤버십 404 → 작업 로드 404 → 합성 권한 403 → **버전 비교 409** → 수정.

409를 403보다 앞에 두면 권한 없는 사용자가 응답 코드 차이로 "다른 사람이 이미
고쳤는지"를 알아낸다. 게이트에 이 순서를 직접 겨눈 항목을 넣었다(항목 7).

## 계획 밖 3건

**1. `TaskResponse.version` — 계획 파일 목록 누락.**
단계 5 파일 목록에 `TaskResponse`가 없다. 그런데 이 방식은 클라이언트가 읽은
version을 되돌려보내는 것이 전제라, 응답에 version이 없으면 성립 자체가 안 된다.
`design-decisions.md`는 세 곳(응답 DTO 포함/감수한 비용/응답 예시 `"version":3`)에서
노출을 이미 확정해 두었다 — 새 결정이 아니라 계획서 목록의 누락이다.

**2. 시드 `version` NULL이면 시드 작업을 아예 수정할 수 없다.**
Hibernate의 versioned UPDATE는 `... WHERE version = ?`다. NULL을 바인딩하면
0건 매칭 → 첫 정당한 수정에서 실패한다. 위생 문제가 아니라 기능 파손이라
INSERT마다 `version` 0을 명시했다. `created_at`/`updated_at`도 `BaseTimeEntity`에서
`nullable = false`라 `CURRENT_TIMESTAMP` 명시.

**3. `data.sql`이 `@DataJpaTest`도 오염시킨다.**
`spring.sql.init.mode` 기본값이 `embedded`이고 슬라이스 DB도 임베디드라
`TaskRepositoryTest`에 시드가 함께 들어온다. 시드 alice와 테스트 alice의 이메일이
UNIQUE 충돌하고, 안 깨지더라도 `totalElements` 단정이 틀어진다.
`@DataJpaTest(properties = "spring.sql.init.mode=never")` 한 줄로 슬라이스를 밀폐했다.
시드를 만든 커밋이 깨뜨리는 것이므로 같은 커밋에 넣었다.

**IDENTITY 시퀀스** — id를 직접 넣으면 H2가 시퀀스를 따라 올리지 않아 첫 POST가
id=1을 재발급한다. 문서에 의존하지 않고 게이트에서 실측(항목 8·9): `RESTART WITH 100`
적용 후 신규 사용자·작업이 각각 id=100으로 발급됐다.

## 커밋 메시지 편차 1건

계획의 2번째 커밋 `feat: RFC 9457 오류 응답 및 OpenAPI 문서 설정 추가`는 실제 diff보다
넓다 — RFC 9457 핸들러는 단계 2(`a25512b`)에서 이미 나갔고 이 커밋의 내용물은
`OpenApiConfig` 하나뿐이다. 있는 것만 쓰도록 `chore: Swagger 전 오퍼레이션에
X-User-Id 헤더 자동 주입`으로 줄였다.

## 게이트 결과

`./gradlew build` 통과. `test --rerun-tasks` 9개 전부 통과
(`CurrentUserArgumentResolverTest` 3, `TaskRepositoryTest` 6 — 시드 도입 후에도 동일).

| # | 검증 | 기대 | 실제 |
|---|---|---|---|
| 1 | alice `GET /api/projects` | P1만 | `['스터디 플랫폼 개편']` total 1 |
| 2 | alice `GET /api/projects/2` | 404 | 404 (비멤버 존재 은닉) |
| 3 | 시드 작업 1 조회 | `version: 0` | `version 0`, 타임스탬프 채워짐 |
| 3b | P1 작업 목록 | total 4 (P2의 5번 제외) | total 4 |
| 4 | `version: 0`으로 PATCH | 200, version 1 | 200, `version 1`, `updatedAt` 갱신 |
| 5 | 낡은 `version: 0`으로 재PATCH | 409 | 409 `Task was modified by another user` |
| 6 | `version` 누락 | 400 + `errors.version` | 400 `{"version":"널이어서는 안됩니다"}` |
| 7 | bob(비담당)이 낡은 version으로 PATCH | **403** (409 아님) | 403 — 순서 유지 확인 |
| 8 | `POST /api/users` | 201, PK 충돌 없음 | 201 `id: 100` |
| 9 | `POST .../tasks` | 201, `version 0` | 201 `id: 100`, `version 0` |
| 10 | P2 작업(id 5)을 P1 경로로 | 404 | 404 |
| 11 | bob이 자기 담당 작업 2 PATCH | 200 | 200, version 1 |
| 12 | Swagger `X-User-Id` 노출 | 전 오퍼레이션 | 16/16 |

`git status` 클린, `docs/` 미추적(`git ls-files docs/` 0건).

## 다음 단계 (6) 진입 시 상기할 것

1. `TaskConcurrencyTest`가 단정할 문자열은 정확히 `Task was modified by another user`
2. `@SpringBootTest`는 `data.sql`이 실행된다 — 시드 id 1~5, `version 0` 전제로 쓰거나
   `spring.sql.init.mode=never`로 밀폐할 것. 둘 중 하나를 의식적으로 고른다
3. README v1 제약 1줄: **담당자 해제 미지원**
4. README에 헤더 방식 근거 1줄(과제 문구의 "파라미터"와 다른 선택) + 시드 계정 표

---

# 단계 6 — 테스트 + README (완료)

커밋 `9618321` `test: 권한·OWNER 불변식·동시성 테스트 추가` / `d579d56` `docs: README 작성`. push 완료(`2f1ec81..d579d56`).

## 만든 것

| 파일 | 방식 | 잠근 것 |
|---|---|---|
| `project/ProjectAccessGuardTest` | Mockito 단위 5케이스 | 비멤버 404 / 역할 부족 403 / **비멤버가 `requireRole`을 거쳐도 404가 먼저** |
| `project/ProjectMemberServiceTest` | `@SpringBootTest` + `@Transactional` 4케이스 | 마지막 OWNER 강등·제거 차단, OWNER 2명이면 강등 허용, 중복 멤버 차단 |
| `task/TaskConcurrencyTest` | `@SpringBootTest` + `@AutoConfigureMockMvc` 1케이스 | 같은 version 2회 PATCH → 200 → 409 → **앞선 변경이 남아 있음** |
| `README.md` | — | 과제 체크리스트 6항 전부 |

## 판단 3건

**1. 테스트마다 방식을 다르게 골랐다.** 가드는 리포지토리 호출 하나가 전부라 스텁이 곧 입력 —
Mockito가 정확하다. 반면 OWNER 불변식의 본체는 `countByProjectIdAndRole` 쿼리라, 이걸 스텁하면
*내가 스텁한 숫자를 내가 검증*하는 꼴이 된다. 실제 리포지토리로 갔다.

**2. `TaskConcurrencyTest`에 테스트 `@Transactional`을 붙이지 않았다.** 붙이면 두 요청이 한
영속성 컨텍스트를 공유해 트랜잭션 밖 충돌이 재현되지 않는다 — 그건 `@Version` 애노테이션만으로도
통과하는 상황이라 아무것도 증명하지 못한다. 요청마다 서비스 트랜잭션이 따로 열리고 커밋되어야
진짜 시나리오다. 대가로 시드 작업의 version이 영구히 오르므로 메서드를 하나로 두고,
현재 version을 GET으로 읽어 시작한다(하드코딩한 `0`이 실행 순서에 낡는 경로를 없앴다).

**3. 409만 확인하고 끝내지 않았다.** 거절은 했는데 이미 반영된 경우를 놓치므로,
마지막에 GET으로 앞선 수정이 살아 있는지 확인한다.

## 단계 5의 미결 2건 처리

- `@SpringBootTest`가 `data.sql`을 싣는 문제 → **밀폐하지 않고 픽스처로 채택**했다.
  셋업 코드가 0줄이 되고, 리뷰어가 curl로 보는 데이터와 테스트가 보는 데이터가 같아진다.
- 두 `@SpringBootTest` 컨텍스트가 같은 이름의 H2(`jdbc:h2:mem:collab`)를 공유하지만,
  컨텍스트 생성 때마다 `ddl-auto: create` + `data.sql`이 다시 돌아 시드로 리셋된다.
  **작업을 읽는 평범한 `@SpringBootTest` 클래스를 하나 더 추가하면** 캐시된 컨텍스트를
  재사용하면서 마지막 생성 컨텍스트가 남긴 상태를 보게 된다 — 추가 시 이 점을 기억할 것.

## 계획 밖 1건

**커밋 메시지 축소.** 계획 문구는 `test: 권한·OWNER 불변식·동시성·검색 테스트 추가`였으나
검색 테스트(`TaskRepositoryTest`)는 단계 4에서 이미 커밋됐다. 실제 diff에 없는 것을 메시지에
쓰지 않는다 — 단계 5의 `chore: Swagger…` 축소와 같은 규칙.

## 게이트 결과

`rm -rf build .gradle && ./gradlew clean build` 통과. 테스트 **19개 전부 통과**
(XML: `failures="0" errors="0" skipped="0"` × 5클래스 — 3+5+4+1+6).

기동 1.769초, swagger 302. 최종 curl 시나리오:

| # | 검증 | 실제 |
|---|---|---|
| 1 | alice `GET /api/projects` | `['스터디 플랫폼 개편']` total 1 |
| 2 | alice `GET /api/projects/2` | 404 |
| 3 | bob `DELETE /api/projects/1` | 403 |
| 4 | 유일 OWNER 강등 | 409 |
| 5 | `version: 0` PATCH | 200, status DONE, version 1 |
| 6 | 낡은 `version: 0` 재PATCH | 409 `Task was modified by another user` |
| 7 | `?keyword=로그인&status=TODO&page=0&size=2` | `['로그인 API 문서 작성']` total 1 |
| 8 | P1 작업 총계 | total 4 |
| 9 | 이메일 형식 위반 | 400 + `errors.email` |
| 10 | 작업 생성 | 201 `Location: /api/projects/1/tasks/100` |
| 11 | Swagger `X-User-Id` | 16/16 |

**README의 모든 예시 응답은 위 실행에서 받은 실물이다.** 지어내지 않았다.

`git status` 클린, `git ls-files docs/` 0건, `gradle-wrapper.jar` 추적됨,
`git grep`으로 키·토큰·비밀번호 부재 확인.

## 남은 것

없음. 단계 0~6 전부 완료. 제출 가능 상태.

## 보강 — README 명령 축자 검증 (커밋 `cdb8f7b`)

"README를 실제 명령으로 따라가며 검증" 게이트를 문자 그대로 다시 돌렸더니 검색 예시만 400이 났다.
게이트 때는 퍼센트 인코딩(`%EB%A1%9C...`)으로 확인했는데 README에는 한글 원문이 적혀 있었다.
**Tomcat은 쿼리 문자열의 비-ASCII 원문을 거절한다**(RFC 3986). 브라우저·Swagger UI는 자동
인코딩하므로 curl에서만 드러난다.

`-G --data-urlencode` 형태로 교체하고, 섹션 2의 7개 명령 전부를 축자 재실행해 확인했다.
교훈: "같은 뜻의 명령"으로 검증하면 문서 검증이 아니다. **복사해 붙일 문자열 그대로** 돌려야 한다.

---

# 단계 7 — 설계·기술 결정 문서 스캔성 개선

커밋 `9b02205` (`docs: 설계 결정 절을 결론 우선 구조로 재배치`), push 완료 `cdb8f7b..9b02205`.

## 입력

사용자가 `ayghri/i-have-adhd`(24.1k stars, MIT)를 지목. 코딩 어시스턴트 출력을 **결론/행동 우선**으로 재구성하는 프롬프트 스킬이다. 10규칙 중 정적 문서에 매핑되는 것은 5개(결론 먼저 · 다단계 번호 · 목록 5개 상한 · 서론/재요약/맺음말 금지 · 곁가지 억제). 나머지 4개(시간 추정치, 매 턴 상태 재기술, 승리 가시화, 담담한 오류 보고)는 **대화 턴 전용**이라 억지 매핑하지 않았다.

사용자 확정: 대상 = README §4·§5 + `design-decisions.md` 둘 다 / 강도 = **선별 적용**(근거 산문은 위치만 내리고 삭제 금지).

## 두 문서의 문제가 서로 달랐다

계획 단계에서 나온 실제 발견이다.

| 문서 | 문제 | 처방 |
|---|---|---|
| `README.md` §4·§5 | 결론이 묻힘 (스타일) | 순서 재배치 |
| `docs/design-decisions.md` | 5행이 `**구현 미착수.**`라고 단언 — 사실이 아님 (목적 변질) | 계획서 → 기록물 전환 |

내부 노트는 이미 `선택 / 기각한 대안 / 감수한 비용` 소제목 구조라 결론 우선이 충족돼 있었다. 여기에 스타일만 손댔으면 헛일이었다.

## README — 절 8개 중 3개만 수정

균일 재작성을 하지 않았다. 절마다 상태를 먼저 확인했다.

| 절 | 확인 결과 | 조치 |
|---|---|---|
| §4.1 동시 수정 충돌 | 결론(`Objects.equals` 비교)이 8줄 뒤 | 결론+코드를 맨 앞으로, 근거 산문을 아래로 |
| §4.3 `X-User-Id` | 과제 문구 인용 뒤에 결론 | 동일 |
| §5 쓰지 않기로 한 기술 | 서론(과제 문구 인용) 뒤에 기준 | 두 문장 순서 교환 |
| §4.2 / §4.4 / §4.5 / §4.6 / §4.7 | **이미 결론이 첫 줄** | 손대지 않음 |

`git diff --stat` = `README.md | 16 ++++----` (8 insertions / 8 deletions). **순수 재배치라 줄 수가 그대로다.**

§4.2는 계획 표에 "서론 문장만 정리"로 적어놨지만 실제로 보니 첫 문장이 이미 판정("이건 취향이 아니라 명세가 강제한 결론입니다")이었다. 마감 하루 전 채점 표면에서 이득 없는 diff를 만들지 않았다.

**5개 상한을 §4.7(6행)·§5 기각 표(6행)에 적용하지 않았다.** 각 행이 독립된 결정 기록이지 작업기억에 담을 목록이 아니다. 잘라내면 결정이 1건 사라진다.

## 계획 밖 1건 — 부록을 통째로 접으면 안 됐다

계획 7.2 2항은 "부록(309~401행) 통째로 1문단 포인터로 접는다"였다. 지우기 전에 중간부(325~373행)를 열어보니 **`도메인 & 스키마` · `권한 게이트` · `REST API` 3절이 확정 스키마와 16개 엔드포인트 권한 표**였다. 실행 계획이 아니라 참조 계약이다. 통째로 접었으면 스키마 기록이 사라진다.

계획보다 **불변식("내용 삭제 0")이 우선**이라고 판단해 절차 절만 걷어냈다.

- 걷어냄: `부트스트랩` · `시드 데이터 & 문서` · `테스트` · `README` · `커밋 단위` · `완료 판정 기준`
- 남김: `도메인 & 스키마` · `권한 게이트` · `REST API`
- 제목 교체: `# 부록 — 구현 계획` → `# 부록 — 확정 스키마 · API 계약`

401행 → 360행(절차 절 제거), 아래 결과 주석 4건을 더해 최종 367행. 삭제 전 백업을 스크래치패드에 떠두고 `diff`로 제거된 헤딩이 의도한 6개뿐임을 확인했다.

## 내부 노트 나머지 수정 4건

1. 헤더 — `**구현 미착수.**` → `**구현 완료** — 4a118f5..9b02205 (커밋 10개, 테스트 19개 통과)`
2. `검증된 버전` — "4건 전부 이 버전 그대로 적용, 조정 없음" 1줄 추가
3. `구현 시 반드시 지킬 함정` — "4건 전부 사전 대응해 터지지 않았다. 대신 여기 없던 결함 5건이 나왔다"로 실제 결과 기록
4. `테스트 범위 — 핵심 규칙 4종` — "4종 전부 작성 + `CurrentUserArgumentResolverTest` 1종 추가돼 5파일 19개" 결과 1줄

전부 **추가만** 했다. 원래 판단은 한 줄도 지우지 않았다 — 면접에서 "왜 그렇게 잡았나"를 설명하려면 착수 전 판단이 남아 있어야 한다.

## 게이트 결과

| 항목 | 결과 |
|---|---|
| `git diff --stat` | `README.md` 1파일. hunk가 196·204·245·252·329행 — 전부 §4·§5 안 |
| 코드 블록 대조 | `TaskService.java:58` = `Objects.equals(request.version(), task.getVersion())` 일치. `ProjectAccessGuard.java:21,27` 시그니처 일치 |
| 내용 보존 | 고유명사 16종(`ETag` `If-Match` `PESSIMISTIC_WRITE` `Last-write-wins` `QueryDSL` `MapStruct` `Flyway` `Spring Security` `Docker` `Redis` `envelope` `ApiResponse` `traceId` `foojay` `OperationCustomizer` `findByIdAndProjectId`) 수정 전후 `grep -c` **전부 동일** |
| `git status` | 클린, `main...origin/main` 동기 |
| `git ls-files docs/` | **0건** |
| 테스트·기동 | 재실행 없음 — 실행되는 것이 하나도 안 바뀌었다 |

## 교훈

**"통째로 지운다"는 계획은 지우기 직전에 한 번 열어봐야 한다.** 계획을 쓸 때는 부록 목차(부트스트랩·도메인·REST API·커밋 단위)만 보고 전부 실행 계획이라고 읽었다. 실제로는 절반이 참조 계약이었다. 단계 6의 "같은 뜻의 명령으로 검증한 건 문서 검증이 아니다"와 같은 종류의 실수 — **요약본으로 판단하고 실물을 안 봤다.**

---

# 단계 8 — 제출 전 결함 3건 수정

`f88c952..87a42d8` (커밋 4개, 테스트 19개 → **24개**). README를 면접관 관점에서 역으로 읽어, **README가 스스로 강조한 축을 정면으로 반박하는** 코드 결함 3건을 찾아 고쳤다. 문서로 방어할 수 없는 종류라 코드를 고쳤다.

## 무엇이 결함이었나

| # | 결함 | README의 어느 문장을 반박하나 |
|---|---|---|
| (a) | `changeRole`/`add`가 **대상 역할**을 제한하지 않음 — ADMIN이 자신을 OWNER로 승격 → 원래 OWNER 강등·제거 → 프로젝트 삭제 | "난이도 핵심은 권한 규칙" 선언 + §4.7 표가 프로젝트 삭제를 OWNER 전용이라 명시 |
| (b) | `remove`가 `Task.assignee`를 비우지 않음 | §4.7이 **직접 쓴 불변식** — "담당자는 프로젝트 멤버여야 한다, 아니면 권한 모델이 스스로 모순된다" |
| (c) | `TaskService.delete`에 version 검사 없음 | 분실 갱신 방어가 PATCH에만 있는 비대칭. 동시 수정 충돌이 이 제출물의 중심 주제다 |

세 결함의 공통점: **README를 쓰면서 세운 규칙을 코드가 한 경로에서만 지키고 있었다.** 문서가 코드보다 먼저 정확해진 상태였다.

## 계획에 없던 발견 2건

### 1. README 워킹트리가 리치텍스트 에디터 왕복으로 손상돼 있었다

단계 8 착수 시 `git status`가 `M README.md`. 내용을 보니 계획과 무관한 미커밋 변경이었다.

| 손상 | 증상 |
|---|---|
| `## 1. 빌드 &` | `&amp;`로 이스케이프 |
| `**`X-User-Id` 헤더**` | 볼드 마커가 백틱 안쪽으로 밀림 (같은 패턴이 `**version` 필수**`, ``**@Version` 컬럼**`에도) |
| `Page<Task>` / `Page<ProjectResponse>` | **`Page[[ORCA_RICH_MD:de834f1c...:inline-html:%3CTask%3E]]`** — 코드 블록 2곳 파괴 |

섞여 있던 실제 문구 수정 3건("정확히 봤습니다"→"살펴봤습니다" 등)은 손상이 아니었다. **discard는 되돌릴 수 없으므로 사용자에게 물었고, "손상만 고치고 문구는 유지"를 선택받았다.** 손상 사이트 5곳을 개별 복구.

교훈: 코드가 안 바뀐 단계였다면 `git status`의 `M README.md`를 "이전 단계 잔여물"로 넘겼을 것이다. **워킹트리가 더러운 채로 시작한 단계는 그 더러움의 출처를 먼저 봐야 한다.**

### 2. 파생 쿼리 이름이 `assigneeId` 속성을 먼저 찾다 실패

```
PathElementException: Could not resolve attribute 'assigneeId' of 'com.example.collab.task.Task'
```

`findAllByProjectIdAndAssigneeId`를 쓰자 **애플리케이션 컨텍스트 자체가 안 떴다** — 7개 테스트 전부 실패. `Task`에는 `assignee`(연관)만 있고 `assigneeId`(스칼라)는 없다. `getAssigneeId()` 게터가 있어도 파서는 메타모델을 본다. `ProjectId`는 우연히 `project.id`로 폴백됐지만 `AssigneeId`는 안 됐다.

→ `findAllByProjectIdAndAssignee_Id`로 언더스코어를 넣어 경로를 명시. 이유를 주석에 남겼다.

교훈: **파생 쿼리 오류는 컴파일 타임이 아니라 컨텍스트 기동 시점에 터진다.** 테스트 7개 전부 실패라는 증상만 보면 원인이 안 보이고, XML 리포트의 `Caused by` 체인을 끝까지 따라가야 나온다.

## 판단 3건

### (a) 규칙을 한 문장으로 잡았다

**"OWNER를 만드는 것도, OWNER를 건드리는 것도 OWNER만."** 가드 하나(`requireOwnerActorForOwnerRole`)를 `add`·`changeRole`·`remove` 세 경로에 건다.

"자기 자신 승격만 차단"은 기각했다 — 증상이지 원인이 아니다. ADMIN 둘이 서로를 OWNER로 올리면 그만이다. 차단선은 대상이 누구냐가 아니라 **OWNER 계층을 건드리느냐**여야 한다.

**추가 쿼리 0.** `accessGuard.requireRole`이 이미 `ProjectMember`를 반환하는데 반환값을 버리고 있었다. 받아 쓰기만 하면 액터 역할을 읽는다.

검사 순서는 기존 규칙 그대로: 멤버십(404) → 역할(403) → 대상 존재(404) → OWNER 가드(403) → 불변식(409).

### (b) 벌크 UPDATE가 아니라 dirty checking

`@Modifying` 벌크 UPDATE는 영속성 컨텍스트를 우회하고 **`@Version`을 올리지 않는다.** 담당자가 풀린 작업을 낡은 version으로 계속 수정할 수 있게 되어, 이 제출물의 중심 주제인 분실 갱신 방어를 스스로 뚫는다. 한 프로젝트에서 한 사람이 맡는 작업 수는 상한이 작아 로드 비용이 문제되지 않는다.

curl 검증에서 실제로 확인: bob 제거 후 작업 2·3의 `version`이 `0 → 1`.

`project` → `task` 패키지 참조는 `ProjectService.delete`의 `taskRepository.deleteByProjectId`에 선례가 있다.

### (c) `?version=` 필수 쿼리 파라미터

DELETE 본문은 명세상 회색지대(프록시·클라이언트마다 취급이 다름), `If-Match`는 Swagger UI에서 손으로 넣어야 해 확인 경험이 나쁘다. 쿼리 파라미터는 둘 다 없고 PATCH 본문의 `version`과 **같은 값을 같은 이름으로** 쓴다.

**선택이 아니라 필수로 했다** — 선택으로 두면 클라이언트가 생략하는 순간 방어가 사라져 있으나 마나 한 안전장치가 된다.

새 예외 핸들러는 필요 없었다. `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속하므로 `MissingServletRequestParameterException`이 그대로 400 ProblemDetail로 나간다 — 실측: `"detail":"Required parameter 'version' is not present."`

`update`의 비교 로직을 `requireCurrentVersion`으로 뽑아 두 경로가 같은 비교·같은 메시지를 쓰게 했다.

## 테스트 5개 추가 (19 → 24)

| 파일 | 추가 | 함정 |
|---|---|---|
| `ProjectMemberServiceTest` | ADMIN 자기 승격 403 / ADMIN의 OWNER 강등 403 | 기존 4개는 액터가 전부 alice(OWNER)라 무영향 |
| `ProjectMemberServiceTest` | bob 제거 후 작업 2·3의 `assigneeId`가 null | `TaskRepository` 주입. `@Transactional` 롤백 그대로 |
| `TaskConcurrencyTest` | 낡은 version DELETE 409 → 맞는 version 204 → GET 404 | **이 클래스는 테스트 `@Transactional`이 없다.** 시드를 지우면 공유 H2에 영구 반영 → POST로 새 작업을 만들어 그것만 지운다 |
| `TaskConcurrencyTest` | version 누락 DELETE 400 | 계획에 없었지만 README가 400을 명시하므로 잠갔다 |

`currentVersion()`을 `currentVersion(String url)`로 일반화하면서 본문의 `TASK_URL` 참조를 바꾸는 걸 한 번 놓쳤다 — 시그니처만 바꾸고 본문을 안 봤다.

## 게이트 결과

| 항목 | 결과 |
|---|---|
| `./gradlew test --rerun-tasks` | XML 5파일 합산 **24개**, `failures="0" errors="0"`. 요약 줄이 아니라 XML을 직접 셌다 |
| `rm -rf build .gradle && ./gradlew clean bootRun` | 클린 상태 기동 성공 |
| curl (a) | `bob→ADMIN` 200 / 자기 승격 403 / OWNER 강등 403 / OWNER 제거 403 — 셋 다 `"Only OWNER can grant or modify OWNER"` |
| curl (b) | bob 제거 204 → 작업 2·3 `"assigneeId":null`, `version` 0→1 |
| curl (c) | 누락 400 / `version=99` 409 / `version=0` 204 / GET 404 |
| Swagger | `/v3/api-docs`의 task DELETE에 `version` query `required=true` 노출, `X-User-Id` 헤더 자동 주입 유지 |
| README §2 재현 | 재기동 후 7개 명령 전부 적힌 응답과 일치 (404 / 403 / 409 / version 1 / 409) |
| `git status` | 클린, `git ls-files docs/` **0건** |

## 계획에 없던 관찰 1건 — 고치지 않았다

`/v3/api-docs`를 보다 발견: `@CurrentUser Long userId`가 **모든** 엔드포인트에 `userId` query 파라미터(`required=true`)로 새어 나온다. 리졸버가 처리하므로 실제로는 무시되지만 Swagger UI에는 쓸모없는 입력란이 하나씩 뜬다.

**단계 8 이전부터 있던 것이고 이번 변경과 무관하다.** 고치려면 `@Parameter(hidden = true)`를 `@CurrentUser`에 붙이거나 `OperationCustomizer`에서 걸러야 하는데, 마감 직전에 전 엔드포인트의 OpenAPI 출력을 바꾸는 변경이라 범위 밖으로 뒀다. 사용자에게 보고했다.

## 내부 노트 수정 (커밋 대상 아님)

`docs/interview-prep.md`:

1. **line 67 사실 오류 정정** — "404 구분은 `detail` 메시지로"는 **틀렸다.** `ProjectAccessGuard`는 프로젝트 부재와 비멤버에 똑같이 `"Project not found: {id}"`를 준다. 메시지를 일부러 맞춘 것이 존재 은닉의 본체다 → "본문은 동일, 구분은 서버 로그"로 수정
2. "지금 유일하게 알고도 못 막은 구멍" → OWNER 불변식 동시성만 남았다는 서술로 조정
3. "테스트를 4개만 짠 기준은" → 24개로 갱신 (4는 재량 지점 수였지 테스트 수가 아니었다)
4. **신규 Q 4건** — OWNER 계층 제한 근거 / 멤버 제거 시 담당자 해제(벌크 UPDATE 기각 이유 포함) / DELETE에 version을 붙인 이유(`?version=` 선택 근거) / **`Page` 직접 노출에 대한 반론 답** (기존엔 장점만 적혀 있었다)

## 범위 밖 (의도적으로 남김)

- **OWNER 최소 1명 불변식의 동시성** — `countByProjectIdAndRole(...) <= 1`이 락 없이 읽힌다. 발생 확률이 낮고, 고치면 "락을 안 쓴 이유"를 설명하는 대비 구조(§4.1의 비관적 락 기각과 짝)가 사라진다. `interview-prep.md`에 정직한 답과 해법(`PESSIMISTIC_WRITE`)이 이미 있다
- **`Page` 직접 노출** — API 형태 변경이라 마감 전 재검증 비용이 크다. 답변으로 방어
- README §7의 '작업 담당자 해제 미지원' 행은 **그대로 뒀다** — PATCH로 해제하는 기능은 여전히 없다. 멤버 제거 시 자동 해제는 별개 경로다

## 교훈

**README를 역으로 읽으면 코드 결함이 나온다.** 세 결함 모두 "README가 선언한 규칙을 코드가 한 경로에서만 지킨다"는 같은 모양이었다. 문서를 쓸 때 규칙이 문장으로 명확해지는데, 그 문장을 다시 코드에 대조하는 패스를 안 하면 문서만 앞서간다. 단계 6·7의 "요약본으로 판단하고 실물을 안 봤다"의 반대 방향 — 이번엔 **문서가 실물보다 정확했다.**

---

# 단계 9 — README에 i-have-adhd 편집 규칙 전면 적용

브랜치 `docs/readme-adhd-style`, 커밋 4개 (`ed0ae4c` §1~§3 / `ba55b8b` §4~§5 / `230309c` §6~§7·상단 / `b81fcb3` 전역 치환).

## 무엇을 했나

10개 규칙을 문서 편집 규칙으로 번역해 적용했다.

| 규칙 | 적용 |
| --- | --- |
| 1 첫 줄은 액션 | 제목 아래 `bootRun` + `swagger-ui` 블록. 각 절의 첫 요소를 표·코드로 |
| 2 다단계는 번호 | §2 curl 5개에 1~5 번호, §6 변경 지점 |
| 3 다음 액션으로 끝 | §2 끝 + 문서 최하단 `다음:` |
| 4 곁가지 억제 | 한글 URL 인코딩 설명을 코드블록 밖 1행 각주로 |
| 5 상태 재진술 | 상단 7행 목차 표 |
| 6 구체적 시간 | 첫 실행 1~2분(실측 1m 22s), 기동 2초(2.194s), 테스트 10초 |
| 7 된 것 가시화 | §2 "지금 되는 것" 5줄 |
| 8 오류는 담담하게 | §3 오류 코드 표 — 이미 통과, 무수정 |
| 9 목록 5개 상한 | §4.7 8→5+3, §5 기술 6→5, 미사용 기술 6→5+1, §6 7→5+2, §7 6→5+1 |
| 10 서두·헤지 제거 | §4 도입문 삭제, §5 기각 기준 산문을 표 아래 1행으로 |

§4는 전부 `**결론.** → 코드 → **왜.** → 기각 표 → **감수한 비용.**` 같은 틀로 맞췄다.

## 검증

| 항목 | 결과 |
| --- | --- |
| `./gradlew clean test` | BUILD SUCCESSFUL in 10s, 24개 |
| 콜드 빌드 (`GRADLE_USER_HOME` 격리) | 1m 22s — README "첫 실행 약 1~2분"의 근거 |
| 기동 | `Started CollabApplication in 2.194 seconds` |
| §2 curl 5개 | 200 / 404 / 403 / (200 후 409) / 검색 200 — 전부 문서와 일치 |
| 멤버 표의 "마지막 OWNER 강등 409" | 실측 409 |
| 엔드포인트 수 | 코드 `@*Mapping` 16개 = README 명세 표 16행 |
| 기각 표 회귀 | ETag·비관적 락·Last-write-wins·전부 404·전부 403·requesterId·헤더+쿼리 폴백 **7행 전부 잔존** |

## 계획과 다른 결과 1건

**줄 수가 목표(240~270)와 반대로 늘었다: 430 → 459.**

계획이 "압축"과 "§4 논증 100% 보존"을 동시에 걸었는데, 규칙 9(5개 상한)를 지키려면 표를 쪼개야 하고 쪼갤 때마다 헤더 2줄 + 소제목이 붙는다. 여기에 상단 액션 블록·목차 표(약 20줄)와 "지금 되는 것" 체크리스트(8줄)가 순증이다. 산문 삭제로 줄인 양보다 구조 추가분이 컸다.

줄 수를 맞추려면 기각 표 행을 지워야 하는데 그건 계획이 명시적으로 금지한 것이라, **줄 수 목표를 버리고 보존 제약을 지켰다.**

## 범위 밖 (의도적으로 남김)

- 목차 표 7행 — 규칙 9의 "목록"이 아니라 참조 자료로 판단해 5개로 쪼개지 않았다. §3 명세 표도 같은 이유로 행 수 유지
- `~라고 봤습니다` / `~라고 판단했습니다` — 스킬 pre-send check 4번("정보를 담은 헤지는 유지")에 따라 남겼다. 지원자가 결정을 자기 것으로 소유한다는 표시라 채점축에 걸린다
- 문서 첫 줄이 여전히 `# Project Collab` — 규칙 1을 문자 그대로 지키면 제목이 사라진다. 규칙보다 문서 정체성을 택했고, 액션은 4줄 아래에 있다

## 교훈

**응답 스타일 규칙을 문서에 그대로 옮기면 "짧게"와 "다 남겨라"가 충돌한다.** 대화 응답은 사라지지만 제출 문서는 근거가 곧 점수라, 두 목표가 부딪히면 보존이 이긴다. 규칙 중 실제로 값을 만든 건 압축 관련(9·10)이 아니라 **배치 관련(1·3·7)** 이었다 — 같은 내용을 순서만 바꿔도 첫 화면에서 실행 가능해진다.

## 후속 수정 (사용자 요청)

목차 표(절-내용)와 §2 시드 작업 표를 제거했다. 둘 다 본문 중복이라는 판단. 시드 작업 정보는 1행 산문으로 대체(프로젝트 1에 4건, 프로젝트 2에 1건). 459 → 437줄.

섹션 번호(`## N.`, `### N.M`)도 전부 제거. 유일한 §참조(문서 최하단 다음 액션)는 제목 인용으로 교체.

---

# 단계 10 — 코드를 반복하는 주석 6곳 제거

브랜치 `docs/readme-adhd-style`.

## 무엇을 했나

`src/` 주석 약 200줄을 전수로 훑고, **코드나 다른 주석을 그대로 되풀이해 정보량이 0인 6곳만** 걷어냈다. WHY 주석은 채점자가 설계 의도를 읽는 경로라 전부 남겼다.

| 파일 | 처리 |
| --- | --- |
| `Project.java` | `update()` 위 "null은 미변경" 블록 삭제 — 바로 아래 `if (name != null)`이 같은 말 |
| `Task.java` | 첫 줄 반복 삭제, `<p>` 문단(담당자 해제 v1 미지원)을 요약 줄로 승격 |
| `UserService.java` | `create()` 위 409 블록 삭제 — `ConflictException` javadoc과 `throw`가 이미 같은 말 |
| `BaseTimeEntity.java` | 클래스 이름 반복 첫 줄 삭제, `<p>` 문단 승격 |
| `CurrentUser.java` | 타입 이름 반복 첫 줄 삭제, `<p>` 문단 승격 |
| `OpenApiConfig.java` | javadoc 2개가 둘 다 "springdoc은 커스텀 resolver를 모른다"를 설명 — 클래스 javadoc 1개로 병합 |

블록을 통째로 지운 건 3곳뿐이고, 나머지 3곳은 **반복 문장만 걷어내고 WHY 문장을 요약 줄로 올렸다.** javadoc 첫 줄이 요약이라는 규약을 지키면서 정보량을 잃지 않는 방식.

## 남긴 것 (의도적)

- `src/test/**` 주석 전부 — 테스트가 왜 그 형태인지(`@Transactional` 미부착 이유 등)는 코드에 안 적힌다
- 예외 3종(`Conflict`/`Forbidden`/`NotFound`) 한 줄 javadoc — 403/404 구분 근거
- `ProjectAccessGuard`·`TaskService`·`ProjectMemberService`·리포지토리의 검사 순서·경계·N+1 주석
- `ProjectService`/`TaskService`의 `saveAndFlush` 주석 — 문장은 같지만 두 지점 모두 로컬 근거가 필요하다. 중복처럼 보여도 한쪽을 지우면 그쪽 코드가 왜 `save`가 아닌지 알 수 없어진다
- `Task.version`의 `@Version` 한계 주석 — 과제가 말한 충돌은 이 애노테이션으로 안 잡힌다는 게 핵심 논거

## 검증

| 항목 | 결과 |
| --- | --- |
| `./gradlew test` | BUILD SUCCESSFUL, 24개 / 실패 0 |
| 컴파일 | 통과 (주석만 바뀌므로 동작 불변) |
| `git diff` 눈 검사 | WHY 주석 딸려 나간 것 없음 |

편집은 문자열 정확 일치 + 유일성 검사를 강제하는 스크립트로 적용했다. 6곳 모두 예상한 한 곳에만 매칭됐다.

## 곁다리로 함께 커밋한 것

이 단계 시작 시점에 이미 미커밋 상태였던 springdoc 수정 3파일(`OpenApiConfig`·`ProjectController`·`TaskController`)이 작업 트리에 있었다. 별도 커밋으로 분리해 함께 올렸다 — `@ParameterObject`를 `Pageable`에 붙여 Swagger가 페이지 파라미터를 펼치게 하고, `@CurrentUser`를 전역 무시시켜 Execute가 막히지 않게 하는 변경.

## 교훈

**"주석 지우기"는 삭제 판단이 아니라 중복 판단이다.** 지운 6곳의 공통점은 문장이 나쁘다는 게 아니라 *같은 파일 안 다른 곳이 이미 그 말을 하고 있다*는 것. 반대로 `saveAndFlush`처럼 문장이 문자 그대로 겹쳐도 두 곳 모두 필요한 경우가 있다 — 기준은 문장의 유일성이 아니라 **그 지점에서 근거가 필요한가**다.

---

# 단계 11 — 내부 노트 stale 정정 + 2종 커밋 전환

브랜치 `docs/readme-adhd-style`.

## 왜 필요했나

`docs/` 4개 문서는 단계 1~6 시점에 쓰였는데 단계 7~10에서 코드와 계약이 계속 바뀌었다. 문서가 따라가지 않아 **현재형으로 쓰인 서술 여러 개가 사실이 아닌 상태**가 됐다. 코드와 전수 대조한 결과 stale은 전부 내부 노트에만 있었고 **README(제출용)는 최신이었다** — 제출물을 고칠 때마다 README를 같이 고쳤고 내부 노트는 안 고쳤다는 뜻이다.

## 커밋 범위 결정 — 4종 중 2종

작업 트리에 `.gitignore`에서 내부 노트 4줄을 지운 미커밋 변경이 있었다. 사용자 결정으로 **2종만 커밋 대상으로 전환**했다.

| 문서 | 처리 | 이유 |
| --- | --- | --- |
| `design-decisions.md` | **커밋** | 기각안과 감수한 비용까지 남아 README보다 결정 과정을 넓게 보여준다 |
| `implementation-log.md` | **커밋** | 결함과 실수까지 시간순으로 남은 기록. 어떻게 걸어왔는지가 결과물만큼 읽을 값이 있다 |
| `spec-gap-policy.md` | 계속 무시 | 채점축을 어떻게 공략할지의 메타 메모 |
| `interview-prep.md` | 계속 무시 | 면접 답변 대본 |

`.gitignore`는 4줄 전체 삭제가 아니라 **2줄만 복원**했다.

## 고친 것

**`implementation-log.md`**

| 위치 | 처리 |
| --- | --- |
| 진행 현황 표 | 단계 3~6이 `대기`로 남아 있었다. 실제로는 10단계 완료 — 11단계까지 커밋 해시와 함께 채움 |
| 머리말 | "`.gitignore`에 등록되어 커밋되지 않는다" → 커밋 전환 사실과 계속 무시되는 2종 명시 |

**`design-decisions.md`**

| 위치 | 처리 |
| --- | --- |
| 머리말 | `4a118f5..9b02205`·커밋 10개·테스트 19개 → `4a118f5..44ec76e`·29개·24개. 커밋 전환 사실 |
| `@CurrentUser` 예시 | `List<ProjectResponse> myProjects(...)` → 실제 시그니처 `Page<ProjectResponse> findMine(@CurrentUser Long userId, @ParameterObject Pageable pageable)` |
| Swagger 대응 | `OperationCustomizer` 1건으로만 서술돼 있었다. 단계 10의 `SpringDocUtils.addAnnotationsToIgnore` + `@ParameterObject`가 왜 필요했는지 한 문단 추가 |
| 테스트 범위 | "5파일 19개다" → 최종 24개. 단계 8에서 5개가 붙은 경위 |
| 부록 머리말 | "구현이 이대로 섰다" → 단계 8에서 계약이 3곳 늘었다는 단서 |
| 권한 게이트 | OWNER 계층 가드(`requireOwnerActorForOwnerRole`)와 멤버 제거 시 `unassign()` 2줄 추가 |
| API 표 DELETE | `?version=` 필수·409·400 표기 (README와 일치) |
| 오류 매핑 | `MissingServletRequestParameterException`→400 추가 |

## 안 고친 것 (의도적)

- **시간순 기록 서술 전부.** 단계 1의 `## .gitignore` 절이 "내부 설계 노트 3종"이라고 쓴 것은 그 시점의 사실이다. 로그는 현재 상태 문서가 아니라 경로 기록이라, 나중 사실로 덮으면 기록의 성질이 깨진다
- `design-decisions.md`의 "결함 5건"·"버전 4건" — 단계 3~6 회고 문맥 안의 숫자
- `spec-gap-policy.md`·`interview-prep.md` 머리말의 `.gitignore` 서술 — **그대로 참**이라 손댈 이유가 없다

## 검증

| 항목 | 결과 |
| --- | --- |
| `grep -rho '@Test' src/test \| wc -l` | 24 — 문서에 쓴 숫자와 일치 |
| `git log --oneline 4a118f5..HEAD \| wc -l` | 29 — 문서에 쓴 숫자와 일치 |
| `git check-ignore docs/*.md` | `spec-gap-policy.md`·`interview-prep.md` 2건만 |
| `git ls-files docs/` | `design-decisions.md`·`implementation-log.md` 2건 |
| `./gradlew test` | BUILD SUCCESSFUL, 24개 / 실패 0 (코드 변경 0이지만 확인) |

## 교훈

**문서가 낡는 지점은 "결과 숫자"가 아니라 "완료 표"였다.** 진행 현황 표가 단계 6에서 멈춘 채 4단계가 더 지나갔다 — 매 단계 본문은 성실히 이어 썼는데 맨 위 요약만 아무도 안 봤다. 요약은 본문보다 먼저 읽히고 나중에 갱신된다. 그래서 **요약에는 파생될 수 있는 값(진행 상태, 개수, 커밋 범위)을 두지 말거나, 둔다면 갱신을 단계 종료 체크리스트에 넣어야 한다.**
