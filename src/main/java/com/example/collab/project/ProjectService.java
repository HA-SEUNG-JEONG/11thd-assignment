package com.example.collab.project;

import com.example.collab.common.exception.NotFoundException;
import com.example.collab.project.dto.ProjectCreateRequest;
import com.example.collab.project.dto.ProjectResponse;
import com.example.collab.project.dto.ProjectUpdateRequest;
import com.example.collab.task.TaskRepository;
import com.example.collab.user.User;
import com.example.collab.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectAccessGuard accessGuard;

    /**
     * 프로젝트 생성은 {@code requireMember}가 덮지 못하는 유일한 진입점이다.
     * resolver가 사용자 존재를 검사하지 않으므로 여기서 명시 확인한다 — 없으면 FK 위반 500이 난다.
     */
    @Transactional
    public ProjectResponse create(Long userId, ProjectCreateRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        Project project = projectRepository.save(new Project(request.name(), request.description()));
        projectMemberRepository.save(new ProjectMember(project, creator, ProjectRole.OWNER));

        return ProjectResponse.from(project);
    }

    public Page<ProjectResponse> findMine(Long userId, Pageable pageable) {
        return projectRepository.findAllByMemberUserId(userId, pageable).map(ProjectResponse::from);
    }

    public ProjectResponse get(Long projectId, Long userId) {
        accessGuard.requireMember(projectId, userId);
        return ProjectResponse.from(loadProject(projectId));
    }

    @Transactional
    public ProjectResponse update(Long projectId, Long userId, ProjectUpdateRequest request) {
        accessGuard.requireRole(projectId, userId, ProjectRole.OWNER, ProjectRole.ADMIN);

        Project project = loadProject(projectId);
        project.update(request.name(), request.description());

        // @LastModifiedDate는 flush 시점(@PreUpdate)에 채워진다. 먼저 flush하지 않으면
        // 응답의 updatedAt이 수정 전 값으로 나간다.
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }

    /**
     * 자식 행을 먼저 지운다. cascade 대신 명시 삭제 — 삭제 경로가 코드에 드러나야 한다.
     * 작업이 멤버를 참조하지는 않지만, 둘 다 프로젝트를 참조하므로 프로젝트보다 먼저 사라져야 한다.
     */
    @Transactional
    public void delete(Long projectId, Long userId) {
        accessGuard.requireRole(projectId, userId, ProjectRole.OWNER);

        taskRepository.deleteByProjectId(projectId);
        projectMemberRepository.deleteByProjectId(projectId);
        projectRepository.delete(loadProject(projectId));
    }

    /** 게이트를 통과한 뒤에도 프로젝트 행이 없다면 데이터 불일치다. 메시지는 게이트와 동일하게 유지. */
    private Project loadProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }
}
