package com.example.collab.project;

import com.example.collab.common.exception.ConflictException;
import com.example.collab.common.exception.ForbiddenException;
import com.example.collab.common.exception.NotFoundException;
import com.example.collab.project.dto.ProjectMemberAddRequest;
import com.example.collab.project.dto.ProjectMemberResponse;
import com.example.collab.project.dto.ProjectMemberRoleUpdateRequest;
import com.example.collab.task.Task;
import com.example.collab.task.TaskRepository;
import com.example.collab.user.User;
import com.example.collab.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectMemberService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectAccessGuard accessGuard;

    public List<ProjectMemberResponse> findAll(Long projectId, Long userId) {
        accessGuard.requireMember(projectId, userId);
        return projectMemberRepository.findAllWithUserByProjectId(projectId).stream()
                .map(ProjectMemberResponse::from)
                .toList();
    }

    /**
     * 검사 순서가 중요하다: 권한 → 대상 존재 → 중복.
     * 중복을 먼저 보면 권한 없는 사용자가 409/201 차이로 멤버십을 탐지할 수 있다.
     */
    @Transactional
    public ProjectMemberResponse add(Long projectId, Long userId, ProjectMemberAddRequest request) {
        ProjectMember actor =
                accessGuard.requireRole(projectId, userId, ProjectRole.OWNER, ProjectRole.ADMIN);
        requireOwnerActorForOwnerRole(actor, request.role());

        User target = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.userId())) {
            throw new ConflictException("User is already a member: " + request.userId());
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        ProjectMember saved = projectMemberRepository.save(
                new ProjectMember(project, target, request.role()));
        return ProjectMemberResponse.from(saved);
    }

    @Transactional
    public ProjectMemberResponse changeRole(Long projectId, Long userId, Long targetUserId,
                                            ProjectMemberRoleUpdateRequest request) {
        ProjectMember actor =
                accessGuard.requireRole(projectId, userId, ProjectRole.OWNER, ProjectRole.ADMIN);

        ProjectMember target = loadMember(projectId, targetUserId);
        requireOwnerActorForOwnerRole(actor, target.getRole());   // OWNER를 건드리는가
        requireOwnerActorForOwnerRole(actor, request.role());     // OWNER를 만드는가

        if (request.role() != ProjectRole.OWNER) {
            requireNotLastOwner(projectId, target);
        }

        target.changeRole(request.role());
        return ProjectMemberResponse.from(target);
    }

    @Transactional
    public void remove(Long projectId, Long userId, Long targetUserId) {
        ProjectMember actor =
                accessGuard.requireRole(projectId, userId, ProjectRole.OWNER, ProjectRole.ADMIN);

        ProjectMember target = loadMember(projectId, targetUserId);
        requireOwnerActorForOwnerRole(actor, target.getRole());
        requireNotLastOwner(projectId, target);

        // 담당자는 멤버여야 한다는 불변식이 제거 경로에서도 유지되어야 한다. 엔티티를 로드해
        // dirty checking으로 비운다 — 벌크 UPDATE는 영속성 컨텍스트를 우회하고 @Version을
        // 올리지 않아, 담당자가 풀린 작업을 낡은 version으로 계속 수정할 수 있다.
        taskRepository.findAllByProjectIdAndAssignee_Id(projectId, targetUserId).forEach(Task::unassign);

        projectMemberRepository.delete(target);
    }

    private ProjectMember loadMember(Long projectId, Long targetUserId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Member not found: " + targetUserId));
    }

    /**
     * OWNER 계층은 OWNER만 만지고 만든다. 이 가드가 없으면 ADMIN이 자신을 OWNER로 승격한 뒤
     * 원래 OWNER를 제거해 OWNER 전용 권한(프로젝트 삭제)까지 도달한다 — 권한 상승이다.
     */
    private void requireOwnerActorForOwnerRole(ProjectMember actor, ProjectRole role) {
        if (role == ProjectRole.OWNER && actor.getRole() != ProjectRole.OWNER) {
            throw new ForbiddenException("Only OWNER can grant or modify OWNER");
        }
    }

    /** OWNER 최소 1명 불변식. 역할 변경과 제거 두 경로가 모두 여기를 통과해야 한다. */
    private void requireNotLastOwner(Long projectId, ProjectMember target) {
        if (target.getRole() == ProjectRole.OWNER
                && projectMemberRepository.countByProjectIdAndRole(projectId, ProjectRole.OWNER) <= 1) {
            throw new ConflictException("Project must have at least one OWNER");
        }
    }
}
