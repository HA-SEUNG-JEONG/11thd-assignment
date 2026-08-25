package com.example.collab.task;

import com.example.collab.common.exception.ConflictException;
import com.example.collab.common.exception.ForbiddenException;
import com.example.collab.common.exception.NotFoundException;
import com.example.collab.project.ProjectAccessGuard;
import com.example.collab.project.ProjectMember;
import com.example.collab.project.ProjectRole;
import com.example.collab.task.dto.TaskCreateRequest;
import com.example.collab.task.dto.TaskResponse;
import com.example.collab.task.dto.TaskUpdateRequest;
import com.example.collab.user.User;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectAccessGuard accessGuard;

    @Transactional
    public TaskResponse create(Long projectId, Long userId, TaskCreateRequest request) {
        ProjectMember member = accessGuard.requireMember(projectId, userId);
        User assignee = resolveAssignee(projectId, request.assigneeId());
        TaskStatus status = request.status() == null ? TaskStatus.TODO : request.status();

        Task task = new Task(
                member.getProject(), request.title(), request.description(), status, assignee);
        return TaskResponse.from(taskRepository.save(task));
    }

    public Page<TaskResponse> search(
            Long projectId, Long userId, String keyword, TaskStatus status, Pageable pageable) {
        accessGuard.requireMember(projectId, userId);
        return taskRepository.search(projectId, normalize(keyword), status, pageable)
                .map(TaskResponse::from);
    }

    public TaskResponse get(Long projectId, Long userId, Long taskId) {
        accessGuard.requireMember(projectId, userId);
        return TaskResponse.from(loadTask(projectId, taskId));
    }

    @Transactional
    public TaskResponse update(
            Long projectId, Long userId, Long taskId, TaskUpdateRequest request) {
        Task task = loadWritableTask(projectId, userId, taskId);

        // 권한(403) 뒤, 수정(409) 앞. 순서가 바뀌면 권한 없는 사용자가 409/403 차이로
        // 다른 사람이 이미 고쳤는지를 알아낸다.
        if (!Objects.equals(request.version(), task.getVersion())) {
            throw new ConflictException("Task was modified by another user");
        }

        User assignee = resolveAssignee(projectId, request.assigneeId());
        task.update(request.title(), request.description(), request.status(), assignee);

        // @LastModifiedDate는 flush 시점(@PreUpdate)에 채워진다. 먼저 flush하지 않으면
        // 응답의 updatedAt이 수정 전 값으로 나간다.
        return TaskResponse.from(taskRepository.saveAndFlush(task));
    }

    @Transactional
    public void delete(Long projectId, Long userId, Long taskId) {
        taskRepository.delete(loadWritableTask(projectId, userId, taskId));
    }

    /**
     * 검사 순서 고정: 멤버십(404) → 작업 로드(404) → 합성 권한(403).
     *
     * <p>순서가 바뀌면 비멤버가 403/404 차이로 작업 존재를 탐지한다.
     * 담당자 조건은 역할 밖이라 {@code requireRole}만으로는 표현되지 않는다 —
     * 그것을 쓰면 담당자 분기 전에 403이 먼저 터진다.
     */
    private Task loadWritableTask(Long projectId, Long userId, Long taskId) {
        ProjectMember member = accessGuard.requireMember(projectId, userId);
        Task task = loadTask(projectId, taskId);

        boolean assigned = userId.equals(task.getAssigneeId());
        if (!member.hasAnyRole(ProjectRole.OWNER, ProjectRole.ADMIN) && !assigned) {
            throw new ForbiddenException("Requires OWNER, ADMIN, or being the assignee");
        }
        return task;
    }

    private Task loadTask(Long projectId, Long taskId) {
        return taskRepository.findByIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
    }

    /**
     * 담당자는 프로젝트 멤버여야 한다 — 비멤버 담당자는 '담당자 본인' 수정 권한을 가지면서도
     * 존재 은닉 때문에 그 작업을 조회하면 404가 되어 권한 모델이 스스로 모순된다.
     * 없는 사용자도 이 멤버 검사에서 404로 걸린다.
     */
    private User resolveAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return accessGuard.requireMember(projectId, assigneeId).getUser();
    }

    /** 빈 문자열을 그대로 넘기면 {@code LIKE '%%'}가 되어 필터가 있는 척한다. */
    private String normalize(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }
}
