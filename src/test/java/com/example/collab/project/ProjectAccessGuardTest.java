package com.example.collab.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.collab.common.exception.ForbiddenException;
import com.example.collab.common.exception.NotFoundException;
import com.example.collab.user.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 접근 거부 코드 규칙(비멤버 404 / 역할 부족 403)은 명세에 없어 재량으로 채운 지점이다.
 * 게이트가 리포지토리 호출 하나로 끝나므로 스텁이 곧 입력이다 — 스프링 컨텍스트 없이 잠근다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectAccessGuardTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long USER_ID = 7L;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @InjectMocks
    private ProjectAccessGuard accessGuard;

    @Test
    @DisplayName("멤버십이 있으면 멤버를 그대로 돌려준다")
    void requireMemberReturnsMembership() {
        ProjectMember member = membership(ProjectRole.MEMBER);
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(member));

        assertThat(accessGuard.requireMember(PROJECT_ID, USER_ID)).isSameAs(member);
    }

    @Test
    @DisplayName("비멤버는 403이 아니라 404 — 프로젝트 존재 자체를 숨긴다")
    void requireMemberHidesExistenceFromNonMember() {
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessGuard.requireMember(PROJECT_ID, USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("허용 역할 중 하나면 통과한다")
    void requireRoleAcceptsAllowedRole() {
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(membership(ProjectRole.ADMIN)));

        assertThat(accessGuard.requireRole(PROJECT_ID, USER_ID, ProjectRole.OWNER, ProjectRole.ADMIN)
                        .getRole())
                .isEqualTo(ProjectRole.ADMIN);
    }

    @Test
    @DisplayName("멤버지만 역할이 부족하면 403 — 경계 안이므로 404로 숨기지 않는다")
    void requireRoleRejectsInsufficientRole() {
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(membership(ProjectRole.MEMBER)));

        assertThatThrownBy(() -> accessGuard.requireRole(PROJECT_ID, USER_ID, ProjectRole.OWNER))
                .isInstanceOf(ForbiddenException.class);
    }

    /** 검사 순서가 뒤집히면 비멤버가 403/404 차이로 프로젝트 존재를 탐지한다. */
    @Test
    @DisplayName("비멤버가 역할 검사를 거치면 403이 아니라 404가 먼저 난다")
    void requireRoleChecksMembershipFirst() {
        when(projectMemberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessGuard.requireRole(PROJECT_ID, USER_ID, ProjectRole.OWNER))
                .isInstanceOf(NotFoundException.class);
    }

    private ProjectMember membership(ProjectRole role) {
        return new ProjectMember(
                new Project("P1", "테스트 프로젝트"), new User("alice", "alice@example.com"), role);
    }
}
