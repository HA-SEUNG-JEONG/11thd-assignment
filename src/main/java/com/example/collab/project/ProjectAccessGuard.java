package com.example.collab.project;

import com.example.collab.common.exception.ForbiddenException;
import com.example.collab.common.exception.NotFoundException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전 도메인이 통과하는 단일 권한 게이트.
 *
 * <p>비멤버에게는 403이 아니라 404를 준다. 403은 "그 리소스는 존재한다"를 알려주기 때문이다.
 * 프로젝트 부재와 멤버십 부재의 메시지도 동일하게 유지해야 존재 은닉이 성립한다.
 */
@Component
@RequiredArgsConstructor
public class ProjectAccessGuard {

    private final ProjectMemberRepository projectMemberRepository;

    public ProjectMember requireMember(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    /** 역할 검사 전에 반드시 멤버십을 먼저 본다 — 비멤버는 403이 아니라 404여야 한다. */
    public ProjectMember requireRole(Long projectId, Long userId, ProjectRole... allowed) {
        ProjectMember member = requireMember(projectId, userId);
        if (!member.hasAnyRole(allowed)) {
            throw new ForbiddenException("Requires one of " + Arrays.toString(allowed));
        }
        return member;
    }
}
