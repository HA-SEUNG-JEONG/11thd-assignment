# Project Collab — 설계 결정 문서 (Design Decisions)

> 작성일 2026-08-24 · 최종 갱신 2026-08-26 · 마감 2026-08-26 23:59
> 구현 전 확정된 설계 결정의 근거 기록. README 3·4·5항의 원본이 됐다.
> **구현 완료** — `4a118f5..44ec76e` (커밋 29개, 테스트 24개 통과). 실행 기록은 `implementation-log.md`.
> 단계 11에서 **커밋 대상으로 전환**됐다 — 기각안과 감수한 비용까지 남은 이 기록이 README보다 결정 과정을 넓게 보여준다. `spec-gap-policy.md`·`interview-prep.md`는 계속 `.gitignore`에 남는다.

---

## 판단 기준

과제 안내서가 명시한 채점축 3개. 아래 모든 결정은 여기에 정렬되어 있다.

1. **명세에 없는 부분을 스스로 결정하고 근거를 남겼는가**
2. **과한 설계는 감점** — "규모에 맞지 않는 구조와 기술은 오히려 감점", "쓰지 않기로 한 판단도 똑같이 좋은 답"
3. **면접에서 "왜 그렇게 설계했고 다른 선택지는 무엇이었나"를 묻는다** → 설명 가능성이 코드량보다 우선

따라서 이 문서의 각 항목은 `선택 / 기각안과 기각 이유 / 감수한 비용` 세 부분을 반드시 갖는다.

---

## 작업 동시 수정 충돌 — 애플리케이션 레벨 버전 비교 + `@Version`

### 문제
과제 명시: "두 사용자가 같은 작업을 동시에 수정하면, 나중 요청이 앞선 변경을 덮어씁니다. 이 상황을 어떻게 다룰지 결정하고 구현하세요."

실제 시나리오는 **트랜잭션 밖**의 충돌이다. A가 화면을 열어 작업을 읽고(트랜잭션1 종료), B가 수정하고, 한참 뒤 A가 저장(트랜잭션2 시작). 두 트랜잭션은 시간적으로 겹치지 않는다. 이 구분이 아래 선택의 전부를 결정한다.

### 선택: 클라이언트가 `version`을 왕복시키고 서비스가 명시 비교, `@Version`은 2차 방어선

```java
// 응답 DTO에 version 포함 → 클라이언트가 수정 요청에 되돌려보냄
if (!Objects.equals(request.version(), task.getVersion())) {
    throw new ConflictException("Task was modified by another user");
}
```

`Task` 엔티티에는 `@Version private Long version;`을 둔다.

**두 겹인 이유 (중요)**
한 요청 안에서 `load → modify → save` 하면 Hibernate는 *방금 읽은* version과 비교하므로, 클라이언트가 낡은 version을 보내도 통과한다 (managed entity에 `setVersion()`을 수동 호출해도 무시됨). 즉 `@Version` 애노테이션만으로는 위 시나리오를 **잡지 못한다.**
- 애플리케이션 레벨 비교 → 트랜잭션 밖 충돌(= 과제가 말한 상황)을 잡음
- `@Version` 컬럼 → 진짜로 겹친 동시 트랜잭션을 DB 레벨에서 잡음 (`ObjectOptimisticLockingFailureException`)

응답은 **409 Conflict** + RFC 9457 `ProblemDetail`.

### 기각한 대안

| 대안 | 기각 이유 |
|---|---|
| **ETag + `If-Match`** | HTTP 표준이고 REST 성숙도는 높아 보이나, 내부적으로 결국 version 비교가 필요하므로 위 선택의 **상위집합**이다. 헤더 변환 레이어만 순수 추가된다. 과제에 조건부 요청·캐싱 요구가 없고, Swagger UI에서 리뷰어가 `If-Match`를 수동 입력해야 해 검증 경험도 나빠진다. 채점축 2에 직격 |
| **비관적 락 (`PESSIMISTIC_WRITE`)** | 기술적으로 틀린 답이다. 락은 트랜잭션 내에서만 유효한데 이 충돌은 트랜잭션 밖에서 발생한다. **요구사항을 해결하지 못하면서 처리량만 깎는다.** 협업 툴처럼 충돌이 드문 워크로드에 락을 거는 것도 비용 대비 손해 |
| **Last-write-wins 유지 + README 서술** | 과제 문장이 "결정하고 **구현**하세요"다. 구현물 0줄이면 미구현으로 읽힐 위험이 크다. 가장 명확한 가점 포인트를 스스로 버리는 선택 |

### 감수한 비용
클라이언트가 `version`을 들고 다녀야 한다. 응답 DTO에 `version` 노출 필요.

### 면접 답변
"협업 툴은 충돌이 드뭅니다. 드문 이벤트를 막으려고 전체 처리량을 깎는 대신, 감지만 하고 재시도를 클라이언트에 위임했습니다. 그리고 `@Version` 애노테이션만으로는 트랜잭션 밖 충돌이 안 잡히기 때문에 서비스에서 명시적으로 비교합니다."

---

## 응답 형식 — 성공은 bare DTO, 오류는 RFC 9457 `ProblemDetail`

### 선택

```
200  { "id":5, "title":"...", "status":"IN_PROGRESS", "version":3 }
목록  Spring Page 구조 (content / totalElements / totalPages / number / size)
4xx  { "type":"...", "title":"...", "status":403, "detail":"...", "instance":"..." }
```

`application.yml`에 `spring.mvc.problemdetails.enabled: true` (Spring Boot 3 내장, 추가 의존성 0).

**근거**
- HTTP 상태코드가 이미 성공/실패를 표현한다. 바디의 `"success": true`는 동일 정보의 이중 표현이다
- 오류를 표준(RFC 9457)으로 고정하면 형식이 한 종류로 수렴한다
- springdoc 스키마가 깨끗하다. 제네릭 래핑은 `ApiResponseProjectResponse` 같은 스키마 이름을 만들어낸다
- 페이징을 `Page`가 그대로 처리해 코드가 0줄이다

### 기각한 대안

| 대안 | 기각 이유 |
|---|---|
| **공통 envelope `ApiResponse<T>`** | 성공/실패를 상태코드와 바디에서 이중 표현한다. 오류는 RFC 9457로 두고 성공만 envelope으로 감싸면 **응답 형식이 오히려 두 종류**가 되어 일관성이 깨진다. envelope의 실익은 여러 팀이 붙은 대규모 API에서 traceId 같은 메타를 실을 자리인데 이 과제엔 그 요구가 없다. 주 수혜자인 프론트엔드도 선택 항목이라 부재한다 |

> 참고: `.claude/skills/rest-api-conventions`와 `problem-details-rfc9457`은 충돌이 아니다. 전자의 description에 "오류 포맷은 후자에 위임"이 명시되어 있다.

---

## 요청자 식별 — `X-User-Id` 헤더 + `@CurrentUser` ArgumentResolver

### 선택
과제: "인증은 구현 대상이 아닙니다. 요청자의 식별자는 API 파라미터로 전달된다고 가정합니다."

```java
@GetMapping("/api/projects")
Page<ProjectResponse> findMine(@CurrentUser Long userId, @ParameterObject Pageable pageable) { ... }
```

`CurrentUserArgumentResolver`가 `X-User-Id` 헤더를 읽는다. 헤더 부재 시 자체 예외 → 400.

**근거**
- 인증을 실제 구현(JWT/세션)으로 교체할 때 **변경 지점이 resolver 1곳**이다. "인증은 구현 대상이 아니다"라는 전제를 설계상 올바른 위치에 격리했음을 보여준다
- 컨트롤러 시그니처가 전부 `@CurrentUser Long userId`로 통일되어 리소스 파라미터와 섞이지 않는다
- **멀티테넌시 확장 답변이 같은 자리에서 이어진다**: `X-Tenant-Id`를 동일 resolver 계층에서 해석 → 한 문단으로 설명 완료

### 기각한 대안

| 대안 | 기각 이유 |
|---|---|
| **쿼리 파라미터 `?requesterId=1`** | 과제 문구에 가장 충실하고 Swagger 검증도 제일 쉽다. 그러나 인증 정보가 리소스 필터와 같은 공간에 섞인다 — `GET /tasks?requesterId=1&assigneeId=2&status=DONE`에서 무엇이 인증이고 무엇이 필터인지 구분되지 않는다. POST/PATCH에서 바디냐 쿼리냐도 애매해진다. 멀티테넌시 답변이 "모든 엔드포인트에 파라미터 하나 더"로 귀결되어 설계 감각이 약해 보인다 |
| **헤더 + 쿼리 폴백 둘 다** | 인증 진입점이 2개가 되어 우선순위 규칙, 둘 다 있을 때/없을 때 분기가 늘어난다. 명세에 없는 유연성이라 채택 시 채점축 2에 걸린다 |

### 감수한 비용 + 해소
"파라미터"의 문자적 해석과 다르다 → README에 근거 1줄 명시.

Swagger 입력 번거로움 → `OpenApiConfig`의 `OperationCustomizer`로 `X-User-Id` 헤더 파라미터를 전 오퍼레이션에 자동 주입(약 10줄). 리뷰어는 한 번만 입력하면 된다.

**단계 10에서 대응이 한 겹 더 붙었다.** 커스텀 resolver 파라미터는 springdoc이 시그니처만으로 알아내지 못해 두 방향으로 어긋났다 — 헤더가 문서에 안 나와 Swagger 호출이 400이 되고(위 `OperationCustomizer`가 해소), `@CurrentUser Long userId`가 **필수 query `userId`로 오해되어 Execute 자체가 막혔다**. 후자는 `SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class)`로 애노테이션을 전역 무시시켜 해소했다. 같은 자리에서 `Pageable`도 `@ParameterObject`를 붙여야 `page`·`size`·`sort`가 개별 파라미터로 펼쳐진다.

---

## 접근 거부 코드 — 비멤버 404, 멤버지만 역할 부족 403

### 선택

| 상황 | 응답 |
|---|---|
| 프로젝트에 속하지 않은 사용자 | **404 Not Found** |
| 멤버지만 요구 역할 미달 (예: MEMBER가 프로젝트 삭제) | **403 Forbidden** |

**근거**
- 과제가 "프로젝트에 속하지 않은 사용자는 **조회를 포함해** 아무것도 할 수 없다"고 강조한다. 403을 주면 "그 ID의 프로젝트는 존재한다"가 새어나가 ID 순회로 프로젝트 존재 여부를 전수 조사할 수 있다
- 반대로 이미 경계 안에 있는 멤버에게 404를 주면 "내 프로젝트가 사라졌나?"로 오해한다. 여기선 403이 정확하고 사용성도 좋다
- 이 2단계 규칙이 그대로 멀티테넌시 답변의 뼈대가 된다: **테넌트 경계 위반 = 404(존재 은닉), 테넌트 내 권한 위반 = 403**
- 서비스 계층 가드가 "멤버십 조회 → 역할 검사" 고정 순서로 자연히 정리된다

### 기각한 대안

| 대안 | 기각 이유 |
|---|---|
| **전부 404** | 과도한 은닉. 멤버인데 권한만 부족한 경우까지 404면 사용자가 혼란스럽고, 얻는 보안 실익이 없다 |
| **전부 403** | 가장 직관적이고 디버깅이 쉽지만, 비멤버에게 리소스 존재를 노출한다. 과제가 강조한 프로젝트 간 격리 요구와 정면 충돌 |

### 감수한 비용
설명할 규칙이 하나 늘어난다 → README에 표 2줄로 해소. 디버깅 시 구분은 `ProblemDetail`의 `detail` 메시지와 서버 로그로 보완.

---

## 패키지 구조 — 도메인형

### 선택

```
com.example.collab
├── common/    BaseTimeEntity, @CurrentUser + Resolver, GlobalExceptionHandler,
│              exception/, OpenApiConfig
├── user/      User, UserRepository, UserService, UserController, dto/
├── project/   Project, ProjectMember, ProjectRole, ProjectAccessGuard,
│              ProjectService, ProjectMemberService, Controller×2, dto/
└── task/      Task, TaskStatus, TaskRepository, TaskService, TaskController, dto/
```

**근거**
- 이 과제의 난이도 핵심은 권한 규칙인데, 그게 전부 `project/` 한 폴더에 응집된다. 리뷰어가 "권한 로직 어디 있나" → 폴더 하나만 열면 끝
- 멀티테넌시 답변이 구조로 뒷받침된다: "`common/`의 resolver와 `BaseTimeEntity`에 `tenantId`만 추가하면 전 도메인 적용"
- 도메인이 3개뿐이라 폴더 폭발이 없다

### 기각한 대안

| 대안 | 기각 이유 |
|---|---|
| **계층형 `controller/ service/ repository/ entity/`** | 스프링 입문 관례라 익숙하다는 게 유일한 장점. `ProjectService`·`TaskService`·`ProjectAccessGuard`가 각각 다른 폴더로 흩어져, 권한 규칙 한 덩어리를 읽으려면 4개 폴더를 왕복해야 한다. 리뷰어 입장에서 손해가 크다 |

> `.claude/skills/layered-architecture`는 **계층 간 책임 분리** 규칙(컨트롤러에 로직 금지, 엔티티 직접 노출 금지)이지 폴더 배치 규칙이 아니다. 도메인형과 충돌하지 않으며, 계층 규칙은 그대로 준수한다.

---

## 작업 목록 검색·필터·페이징 — 단일 `@Query` + null 허용 조건

### 선택

```java
@Query("""
    SELECT t FROM Task t
    WHERE t.project.id = :projectId
      AND (:keyword IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:status  IS NULL OR t.status = :status)
    """)
Page<Task> search(Long projectId, String keyword, TaskStatus status, Pageable pageable);
```

**근거**
- 메서드 1개로 끝나고 빌드 설정 추가가 0이다
- `Pageable`이 페이징·정렬을 전부 처리하며 응답도 `Page` 그대로 나간다 (응답 형식 결정과 일치)
- 조건이 3개로 고정이고 늘어날 계획이 없다. 동적 쿼리 빌더의 존재 이유가 없다
- 프로젝트 경계(`t.project.id = :projectId`)가 **쿼리 안에** 있어, 애플리케이션에서 거르는 방식과 달리 타 프로젝트 데이터 혼입이 구조적으로 불가능하다

### 기각한 대안

| 대안 | 기각 이유 |
|---|---|
| **QueryDSL** | 타입 안전·동적 조합에서 우수하나 비용이 실질적이다. Gradle annotation processor 설정 + Q타입 생성 + 빌드 경로 관리가 붙어, **"클론 후 `./gradlew bootRun` 한 번"** 요구에 실패 리스크를 추가한다. 얻는 이득은 조건 3개짜리 쿼리 하나다. README에 "왜 필요했는지"를 정직하게 쓰면 "필요 없었다"가 된다 — 채점축 2의 가장 정확한 사례 |
| **Spring Data `Specification`** | 의존성은 0이지만 Criteria API 특유의 장황함으로 코드가 `@Query`보다 길어진다. 조건 3개에서는 열위 |

### 감수한 비용
조건이 6~7개로 늘면 가독성이 무너진다. 이 과제 범위에서는 발생하지 않으며, 그때 QueryDSL 도입이 정당해진다.

---

## 테스트 범위 — 핵심 규칙 4종

### 선택

| 파일 | 검증 대상 |
|---|---|
| `ProjectAccessGuardTest` | 역할×기능 매트릭스, 비멤버 404, 역할 부족 403 |
| `ProjectMemberServiceTest` | 마지막 OWNER 역할변경·제거 차단, 중복 멤버 추가 차단 |
| `TaskConcurrencyTest` (`@SpringBootTest`) | 동일 `version`으로 2회 수정 → 두 번째 409 |
| `TaskRepositoryTest` (`@DataJpaTest`) | keyword/status 조합, 페이징 경계, 타 프로젝트 작업 미포함 |

**근거**
- 과제가 "완성도보다 문제 접근 방식과 설계 의도"를 본다고 명시했다. 테스트는 *무엇을 중요하게 봤는가*의 증거물이다
- 위 4개가 정확히 이 과제의 어려운 지점 전부다 (동시 수정 충돌, 접근 거부 코드, OWNER 불변식, 데이터 격리)
- 커버리지 숫자가 아니라 판단력을 보여주며, 2일 안에 실현 가능하다

**결과: 4종 전부 작성됐고 `CurrentUserArgumentResolverTest`가 1종 추가돼 5파일 19개가 됐다.** 추가분은 단계 2에 `@CurrentUser` 엔드포인트가 아직 없어 curl로 resolver를 검증할 수 없어서 넣었다 — 재량으로 채운 곳일수록 테스트로 잠근다는 같은 기준.

**최종은 5파일 24개다.** 단계 8에서 제출 전 결함 3건을 고치며 5개가 붙었다 — OWNER 계층 변경 제한, 멤버 제거 시 담당자 해제, 작업 삭제의 version 검사. 셋 다 그 단계에서 새로 세운 규칙이라 같은 기준이 그대로 적용됐다.

### 기각한 대안

| 대안 | 기각 이유 |
|---|---|
| **피라미드 전체 (`@WebMvcTest` 슬라이스 포함)** | 로직이 서비스 계층에 있어 컨트롤러 슬라이스의 검증 가치가 낮다. 남은 시간 대비 비용 과다 |
| **테스트 없음** | "코드 품질 중심 평가"에서 가장 싼 가점을 버린다. 특히 동시 수정 충돌 처리는 테스트 없이는 동작을 증명할 수 없다 (`@Version` 단독으로는 안 잡히는 함정이 있어 더욱 그렇다) |

---

## 멀티테넌시 확장 방안 (README 필수 질문)

> "여러 회사가 이 서비스를 함께 쓰고 회사끼리 데이터가 완전히 분리되어야 한다면, 어디를 어떻게 바꾸겠는가"

### 격리 모델 3안 비교

| 모델 | 격리 강도 | 운영 비용 | 이 규모에서의 판단 |
|---|---|---|---|
| DB per tenant | 최상 (물리 분리) | 최상 — 테넌트마다 인스턴스·백업·마이그레이션 | 과함 |
| Schema per tenant | 강 (논리 분리) | 중 — 스키마별 마이그레이션 실행 필요 | 규제 요구 생기면 후보 |
| **공유 스키마 + discriminator 컬럼** | 중 — 애플리케이션이 경계를 보장 | 최소 | **채택** |

공유 스키마를 고르는 이유: 이 서비스는 이미 `project_members`로 애플리케이션 레벨 경계를 강제하고 있다. 같은 메커니즘을 한 단계 위(`tenant_id`)로 올리는 것이 가장 작은 변경이며, 규제 요구가 생기면 schema-per-tenant로 승격하는 경로가 열려 있다.

### 구체적 변경 지점

1. **진입** — `CurrentUserArgumentResolver`와 같은 자리에서 `X-Tenant-Id` 해석. 인증 도입 시엔 토큰 클레임에서 추출. 인터셉터/필터가 `TenantContext`(ThreadLocal)에 저장하고 요청 종료 시 반드시 clear
2. **영속성** — `BaseTimeEntity`에 `tenantId` 추가 후 Hibernate `@TenantId`(또는 `@Filter` + `CurrentTenantIdentifierResolver`)로 모든 조회·저장에 자동 적용. **개별 쿼리에 조건을 손으로 추가하지 않는다** — 한 곳이라도 빠지면 격리가 깨지므로
3. **제약** — `users.email`의 유니크를 `(tenant_id, email)`로 변경. 모든 유니크·FK 제약을 테넌트 스코프로 재검토
4. **인덱스** — 조회 인덱스 선두에 `tenant_id` 배치 (`(tenant_id, project_id, status)`)
5. **오류 정책** — **접근 거부 코드 규칙이 그대로 확장된다.** 크로스 테넌트 접근은 404(존재 은닉), 테넌트 내 권한 위반은 403. 새 규칙을 만들 필요가 없다
6. **경계 밖 코드** — 배치/스케줄러/캐시 키에 `tenantId`를 반드시 포함. 캐시 키 누락은 테넌트 간 데이터 유출로 직결된다
7. **검증** — 테넌트 A 컨텍스트에서 테넌트 B 리소스 접근 시 404를 확인하는 통합 테스트를 격리 회귀 방지선으로 둔다

---

## 사용하지 않기로 한 기술 (채택만큼 중요한 결정)

과제: "쓰지 않기로 한 판단도 똑같이 좋은 답입니다."

| 기술 | 결정 | 이유 |
|---|---|---|
| QueryDSL | 미사용 | 검색 구현 항목 참조. 조건 3개 고정에 빌드 설정 비용이 `bootRun` 한 번 요구를 위협 |
| MapStruct | 미사용 | DTO가 10개 미만. `TaskResponse.from(task)` static factory가 더 짧고 빌드 설정이 0 |
| Flyway / Liquibase | 미사용 | H2 인메모리 + `ddl-auto: create`. 마이그레이션 대상 자체가 존재하지 않음 |
| Spring Security | 미사용 | 인증이 구현 대상이 아님. 도입하면 `permitAll` 설정만 늘어나고 권한 로직은 여전히 서비스 계층에 남음 |
| Docker / CI | 미사용 | 요구가 "클론 후 `./gradlew bootRun` 한 번". 추가 레이어는 실행 경로만 늘림 |
| Redis / Kafka / MongoDB | 미사용 | 캐싱·이벤트·문서 저장 요구가 명세에 없음 |
| Lombok | **사용** | 보일러플레이트 제거. 사실상 표준이라 설명 비용이 1줄 |

---

## 기타 확정 사항

| 항목 | 결정 | 근거 |
|---|---|---|
| 작업 상태 | `TODO / IN_PROGRESS / DONE` | 과제가 지원자 재량으로 명시. 3단계면 충분하고, 상태 전이 규칙은 요구가 없어 두지 않음 |
| 삭제 | Hard delete | Soft delete 요구 없음. 도입 시 모든 조회에 필터 조건이 붙는 비용만 발생 |
| 작업 URI | `/api/projects/{id}/tasks/...` 중첩 | 프로젝트 경계가 URI에 드러나 권한 검사 지점이 명시적이 됨 |
| 시드 데이터 | `data.sql` | 체크리스트가 "초기 데이터를 넣어두면 좋습니다" 명시. 리뷰어가 Swagger에서 즉시 404/403/409 시나리오 검증 가능 |
| Java 버전 | Gradle toolchain `languageVersion 17` + foojay-resolver 0.10.0 | 로컬엔 JDK 21만 있고 채점자 환경은 미상. toolchain이 JDK 17을 자동 조달해 어디서든 동작 |

---

## 검증된 버전 (Maven Central / services.gradle.org 실제 조회)

**결과: 4건 전부 이 버전 그대로 적용됐고 클린 빌드·기동에 성공했다.** 조정 없음.

| 항목 | 버전 | 비고 |
|---|---|---|
| Spring Boot | **3.3.13** | 3.3.x 최신 패치. start.spring.io는 3.3을 더 이상 제공하지 않음(HTTP 400) → `build.gradle` 수기 작성 |
| springdoc-openapi-starter-webmvc-ui | **2.6.0** | Boot 3.3 대응. 2.7+/2.8은 Boot 3.4/3.5용이라 불일치 |
| Gradle | **8.14.5** | 8.x 최신 |
| foojay-resolver-convention | **0.10.0** | Gradle 8.x 호환 (1.0.0은 Gradle 9 계열) |

---

## 구현 시 반드시 지킬 함정

**결과: 4건 전부 대응을 사전 적용해 실제로 터지지 않았다.** 대신 여기 없던 결함 5건이 나왔다 — `updatedAt` 스냅샷 시점 / `TaskResponse.version` 누락 / 시드 `version` NULL이면 versioned UPDATE 파손 / `data.sql`이 `@DataJpaTest` 오염 / README 검색 예시가 축자 실행 시 Tomcat 400. 전부 `implementation-log.md`에 원인과 대응 기록.

| 함정 | 대응 |
|---|---|
| `@Version` 단독으로는 과제가 말한 충돌을 못 잡는다 | 서비스에서 `request.version` vs `task.getVersion()` 명시 비교 |
| `data.sql`이 Hibernate DDL보다 먼저 실행됨 (Boot 2.5+) → "table not found" 기동 실패 | `spring.jpa.defer-datasource-initialization: true` |
| `MissingRequestHeaderException`은 `@RequestHeader`에만 발생 | `@CurrentUser` resolver가 헤더 부재 시 자체 예외 → 400 |
| `(:param IS NULL OR ...)` JPQL이 Hibernate 6 / H2에서 파라미터 타입 추론에 걸릴 수 있음 | 쿼리 작성 직후 `@DataJpaTest` 즉시 실행 |

---

# 부록 — 확정 스키마 · API 계약

> 착수 전 확정한 계약이고, 구현이 이대로 섰다. **단계 8에서 3곳이 늘었다** — OWNER 계층 가드, 멤버 제거 시 담당자 해제, 작업 삭제의 version 검사. 아래 표에 반영돼 있다.
> **실행 절차(부트스트랩·시드·테스트 순서·커밋 단위·완료 판정)는 전 항목 실행 완료**되어 여기서 걷어냈다.
> 실제로 걸어간 경로와 계획 밖 결함 5건은 `implementation-log.md`가 SSOT다.

## 도메인 & 스키마
테이블 4개.
- `users` — id, name, email(unique)
- `projects` — id, name, description, created_at, updated_at
- `project_members` — id, project_id, user_id, role, **unique(project_id, user_id)**
- `tasks` — id, project_id, title, description, status, assignee_id(nullable), **version**, created_at, updated_at / 인덱스 `(project_id, status)`

권한은 `project_members` 조인으로 판정. 별도 권한 테이블 없음.

## 권한 게이트
`ProjectAccessGuard` 하나로 전 도메인이 통과. 고정 순서:
```java
ProjectMember requireMember(Long projectId, Long userId);                       // 실패 → 404
ProjectMember requireRole(Long projectId, Long userId, ProjectRole... allowed); // 실패 → 403
```
- 모든 프로젝트/작업 서비스 메서드의 첫 줄이 이 호출
- 목록은 `WHERE project_id IN (내 멤버십)` — 애플리케이션 필터링 금지
- 작업 수정/삭제: `담당자 본인 || OWNER || ADMIN`
- **OWNER 최소 1명 불변식**: 역할 변경·제거 시 `대상.role == OWNER && OWNER 수 == 1` → 409
- **OWNER 계층 가드** (`requireOwnerActorForOwnerRole`, 단계 8): OWNER를 만드는 것도 OWNER를 건드리는 것도 OWNER만 → 403. 없으면 ADMIN이 자기를 OWNER로 올린 뒤 원래 OWNER를 내리는 2단계 권한 상승이 뚫린다
- **멤버 제거 시 담당 작업 `unassign()`** (단계 8): 벌크 UPDATE가 아니라 엔티티 로드 후 dirty checking — 벌크는 `@Version`을 올리지 않아 낡은 version이 계속 통한다
- 프로젝트 생성 시 생성자를 OWNER 멤버로 같은 트랜잭션에서 등록

## REST API

| 메서드 | 경로 | 권한 |
|---|---|---|
| POST | `/api/users` | — |
| GET | `/api/users/{id}` | — |
| POST | `/api/projects` | 누구나 (생성자 OWNER) |
| GET | `/api/projects` | 내가 속한 목록 |
| GET | `/api/projects/{id}` | 멤버 |
| PATCH | `/api/projects/{id}` | OWNER, ADMIN |
| DELETE | `/api/projects/{id}` | OWNER |
| GET | `/api/projects/{id}/members` | 멤버 |
| POST | `/api/projects/{id}/members` | OWNER, ADMIN |
| PATCH | `/api/projects/{id}/members/{userId}` | OWNER, ADMIN |
| DELETE | `/api/projects/{id}/members/{userId}` | OWNER, ADMIN |
| POST | `/api/projects/{id}/tasks` | 멤버 |
| GET | `/api/projects/{id}/tasks` | 멤버 — `?keyword=&status=&page=&size=&sort=` |
| GET | `/api/projects/{id}/tasks/{taskId}` | 멤버 |
| PATCH | `/api/projects/{id}/tasks/{taskId}` | 담당자 / OWNER / ADMIN — 바디에 `version` 필수 |
| DELETE | `/api/projects/{id}/tasks/{taskId}` | 담당자 / OWNER / ADMIN — `?version=` 필수 (불일치 409, 누락 400) |

오류 매핑 (`GlobalExceptionHandler` → `ProblemDetail`): `NotFoundException`→404, `ForbiddenException`→403, `ConflictException`→409, `ObjectOptimisticLockingFailureException`→409, `MethodArgumentNotValidException`→400(필드 오류 목록), `MissingServletRequestParameterException`→400(`?version=` 누락), `X-User-Id` 부재→400.

`application.yml`: `spring.mvc.problemdetails.enabled: true`, `spring.jpa.defer-datasource-initialization: true`, `springdoc.swagger-ui.path: /swagger-ui.html`, H2 콘솔 활성화.

