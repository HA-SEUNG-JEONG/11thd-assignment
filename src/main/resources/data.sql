-- 리뷰어가 기동 직후 Swagger에서 바로 권한 시나리오를 볼 수 있게 하는 최소 시드.
-- P2는 alice·bob이 비멤버라 "비멤버 404"를 사용자 생성 없이 재현할 수 있다.
--
-- ddl-auto: create 로 매 기동 스키마를 새로 만든 뒤 실행된다
-- (spring.jpa.defer-datasource-initialization: true — 없으면 table not found).

INSERT INTO users (id, name, email) VALUES
  (1, 'alice', 'alice@example.com'),
  (2, 'bob',   'bob@example.com'),
  (3, 'carol', 'carol@example.com');

INSERT INTO projects (id, name, description, created_at, updated_at) VALUES
  (1, '스터디 플랫폼 개편', 'alice가 OWNER, bob이 MEMBER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, '사내 위키 이관',   'carol만 참여 — alice·bob은 비멤버',  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO project_members (id, project_id, user_id, role) VALUES
  (1, 1, 1, 'OWNER'),
  (2, 1, 2, 'MEMBER'),
  (3, 2, 3, 'OWNER');

-- version 0 명시. NULL이면 Hibernate의 versioned UPDATE가 `WHERE version = NULL`이 되어
-- 시드 작업을 처음 수정하는 순간 0건 매칭으로 실패한다.
INSERT INTO tasks (id, project_id, title, description, status, assignee_id, version, created_at, updated_at) VALUES
  (1, 1, '로그인 화면 리뉴얼',   'assignee: alice',        'IN_PROGRESS', 1,    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (2, 1, '로그인 API 문서 작성', 'assignee: bob',          'TODO',        2,    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (3, 1, '페이지네이션 버그 수정', 'assignee: bob',         'DONE',        2,    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (4, 1, '검색 필터 추가',       '담당자 미지정',            'TODO',        NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (5, 2, '위키 문서 목록 정리',   'P2 소속 — P1로는 조회되지 않는다', 'TODO', 3,    0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- id를 직접 넣으면 IDENTITY 시퀀스가 따라 올라가지 않는다. 재시작 없이는 POST 첫 요청이
-- id=1을 다시 발급받아 PK 충돌이 난다.
ALTER TABLE users           ALTER COLUMN id RESTART WITH 100;
ALTER TABLE projects        ALTER COLUMN id RESTART WITH 100;
ALTER TABLE project_members ALTER COLUMN id RESTART WITH 100;
ALTER TABLE tasks           ALTER COLUMN id RESTART WITH 100;
