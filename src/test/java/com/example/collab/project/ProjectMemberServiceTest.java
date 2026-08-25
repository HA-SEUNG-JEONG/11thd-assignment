package com.example.collab.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.collab.common.exception.ConflictException;
import com.example.collab.common.exception.ForbiddenException;
import com.example.collab.project.dto.ProjectMemberAddRequest;
import com.example.collab.project.dto.ProjectMemberResponse;
import com.example.collab.project.dto.ProjectMemberRoleUpdateRequest;
import com.example.collab.task.Task;
import com.example.collab.task.TaskRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * "프로젝트에는 항상 최소 1명의 OWNER가 있어야 한다"는 과제가 명시한 불변식이고,
 * 위반 시 어떤 코드를 줄지는 재량이라 잠근다.
 *
 * <p>불변식의 본체는 {@code countByProjectIdAndRole} 쿼리다. 모킹하면 스텁한 숫자를
 * 스스로 검증하는 꼴이라 실제 리포지토리로 돌린다. 시드({@code data.sql})가 픽스처다 —
 * P1은 alice(1)=OWNER, bob(2)=MEMBER. 테스트 트랜잭션은 끝에서 롤백된다.
 */
@SpringBootTest
@Transactional
class ProjectMemberServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;

    /** 시드에서 bob이 담당하는 P1 작업. */
    private static final List<Long> BOB_TASKS = List.of(2L, 3L);

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    @DisplayName("유일한 OWNER를 다른 역할로 바꾸면 409")
    void cannotDemoteLastOwner() {
        assertThatThrownBy(() -> projectMemberService.changeRole(
                        PROJECT_ID, ALICE, ALICE, new ProjectMemberRoleUpdateRequest(ProjectRole.MEMBER)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("유일한 OWNER를 제거하면 409 — 역할 변경과 제거 두 경로 모두 막힌다")
    void cannotRemoveLastOwner() {
        assertThatThrownBy(() -> projectMemberService.remove(PROJECT_ID, ALICE, ALICE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("OWNER가 2명이면 강등이 허용된다 — 막는 것은 '마지막'뿐")
    void demotionAllowedWhenAnotherOwnerRemains() {
        projectMemberService.changeRole(
                PROJECT_ID, ALICE, BOB, new ProjectMemberRoleUpdateRequest(ProjectRole.OWNER));

        ProjectMemberResponse demoted = projectMemberService.changeRole(
                PROJECT_ID, ALICE, ALICE, new ProjectMemberRoleUpdateRequest(ProjectRole.MEMBER));

        assertThat(demoted.role()).isEqualTo(ProjectRole.MEMBER);
    }

    /**
     * 권한 상승 경로를 잠근다: ADMIN이 자신을 OWNER로 올릴 수 있으면 그 다음 원래 OWNER를 제거해
     * OWNER 전용 권한(프로젝트 삭제)까지 도달한다. 두 테스트가 그 사슬의 두 고리를 각각 끊는다.
     */
    @Test
    @DisplayName("ADMIN이 자신을 OWNER로 승격하면 403")
    void adminCannotPromoteSelfToOwner() {
        projectMemberService.changeRole(
                PROJECT_ID, ALICE, BOB, new ProjectMemberRoleUpdateRequest(ProjectRole.ADMIN));

        assertThatThrownBy(() -> projectMemberService.changeRole(
                        PROJECT_ID, BOB, BOB, new ProjectMemberRoleUpdateRequest(ProjectRole.OWNER)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("ADMIN이 OWNER를 강등하면 403 — OWNER를 건드리는 것도 OWNER만 가능하다")
    void adminCannotDemoteOwner() {
        projectMemberService.changeRole(
                PROJECT_ID, ALICE, BOB, new ProjectMemberRoleUpdateRequest(ProjectRole.ADMIN));

        assertThatThrownBy(() -> projectMemberService.changeRole(
                        PROJECT_ID, BOB, ALICE, new ProjectMemberRoleUpdateRequest(ProjectRole.MEMBER)))
                .isInstanceOf(ForbiddenException.class);
    }

    /**
     * "담당자는 프로젝트 멤버여야 한다"는 불변식이 제거 경로에서도 성립하는지 잠근다.
     * 비우지 않으면 제거된 사용자가 담당자로 남고, 본인은 그 작업을 조회할 때 404를 받는다.
     */
    @Test
    @DisplayName("멤버를 제거하면 그 사람이 담당하던 작업의 담당자가 비워진다")
    void removingMemberUnassignsTheirTasks() {
        assertThat(assigneeIdsOf(BOB_TASKS)).containsOnly(BOB);

        projectMemberService.remove(PROJECT_ID, ALICE, BOB);

        assertThat(assigneeIdsOf(BOB_TASKS)).containsOnlyNulls();
    }

    @Test
    @DisplayName("이미 멤버인 사용자를 다시 추가하면 409")
    void cannotAddDuplicateMember() {
        assertThatThrownBy(() -> projectMemberService.add(
                        PROJECT_ID, ALICE, new ProjectMemberAddRequest(BOB, ProjectRole.MEMBER)))
                .isInstanceOf(ConflictException.class);
    }

    private List<Long> assigneeIdsOf(List<Long> taskIds) {
        return taskIds.stream()
                .map(id -> taskRepository.findById(id).orElseThrow())
                .map(Task::getAssigneeId)
                .toList();
    }
}
