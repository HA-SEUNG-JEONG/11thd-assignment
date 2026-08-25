package com.example.collab.project.dto;

import com.example.collab.project.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record ProjectMemberRoleUpdateRequest(@NotNull ProjectRole role) {
}
