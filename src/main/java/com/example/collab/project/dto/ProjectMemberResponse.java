package com.example.collab.project.dto;

import com.example.collab.project.ProjectMember;
import com.example.collab.project.ProjectRole;
import com.example.collab.user.User;

public record ProjectMemberResponse(
        Long userId,
        String name,
        String email,
        ProjectRole role) {

    /** user는 LAZY다. JOIN FETCH로 이미 초기화된 상태에서만 호출한다. */
    public static ProjectMemberResponse from(ProjectMember member) {
        User user = member.getUser();
        return new ProjectMemberResponse(user.getId(), user.getName(), user.getEmail(), member.getRole());
    }
}
