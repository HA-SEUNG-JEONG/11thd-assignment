package com.example.collab.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.collab.project.Project;
import com.example.collab.user.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * {@code (:param IS NULL OR ...)} 조합 쿼리가 Hibernate 6 / H2에서 실제로 도는지 잠근다.
 * 검색·필터·페이징은 재량으로 채운 지점이라 회귀가 조용히 난다.
 *
 * <p>{@code spring.sql.init.mode=never}: 슬라이스도 임베디드 DB라 {@code data.sql}이 함께 돌아
 * 시드 사용자·작업이 섞여 들어온다. 이 테스트는 자기가 넣은 행만 세야 한다.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private EntityManager entityManager;

    private Long projectId;
    private Long otherProjectId;

    @BeforeEach
    void setUp() {
        User alice = persist(new User("alice", "alice@example.com"));
        Project project = persist(new Project("P1", "첫 프로젝트"));
        Project other = persist(new Project("P2", "다른 프로젝트"));
        projectId = project.getId();
        otherProjectId = other.getId();

        persist(new Task(project, "Design login page", null, TaskStatus.TODO, alice));
        persist(new Task(project, "Write LOGIN docs", null, TaskStatus.DONE, null));
        persist(new Task(project, "Fix pagination", null, TaskStatus.TODO, null));
        persist(new Task(other, "Design landing page", null, TaskStatus.TODO, null));

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("keyword·status가 모두 null이면 해당 프로젝트 작업 전부")
    void searchWithoutFilters() {
        Page<Task> page = taskRepository.search(projectId, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(titles(page)).doesNotContain("Design landing page");
    }

    @Test
    @DisplayName("keyword는 대소문자를 가리지 않고 제목 부분 일치")
    void searchByKeyword() {
        Page<Task> page = taskRepository.search(projectId, "login", null, PageRequest.of(0, 10));

        assertThat(titles(page)).containsExactlyInAnyOrder("Design login page", "Write LOGIN docs");
    }

    @Test
    @DisplayName("status 단독 필터")
    void searchByStatus() {
        Page<Task> page =
                taskRepository.search(projectId, null, TaskStatus.TODO, PageRequest.of(0, 10));

        assertThat(titles(page)).containsExactlyInAnyOrder("Design login page", "Fix pagination");
    }

    @Test
    @DisplayName("keyword와 status는 AND로 결합된다")
    void searchByKeywordAndStatus() {
        Page<Task> page =
                taskRepository.search(projectId, "login", TaskStatus.TODO, PageRequest.of(0, 10));

        assertThat(titles(page)).containsExactly("Design login page");
    }

    @Test
    @DisplayName("페이징은 프로젝트 경계 안에서만 계산된다")
    void searchPaging() {
        Page<Task> first = taskRepository.search(projectId, null, null, PageRequest.of(0, 2));
        Page<Task> second = taskRepository.search(projectId, null, null, PageRequest.of(1, 2));

        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("단건 조회는 프로젝트 경계를 함께 건다 — 타 프로젝트 작업은 비어 있다")
    void findByIdAndProjectIdBlocksCrossProject() {
        Long foreignTaskId = taskRepository
                .search(otherProjectId, null, null, PageRequest.of(0, 1))
                .getContent()
                .get(0)
                .getId();

        assertThat(taskRepository.findByIdAndProjectId(foreignTaskId, projectId)).isEmpty();
        assertThat(taskRepository.findByIdAndProjectId(foreignTaskId, otherProjectId)).isPresent();
    }

    private List<String> titles(Page<Task> page) {
        return page.getContent().stream().map(Task::getTitle).toList();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
