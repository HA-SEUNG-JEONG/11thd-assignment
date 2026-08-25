package com.example.collab.project.dto;

import com.example.collab.project.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record ProjectMemberAddRequest(
        @NotNull Long userId,
        @NotNull ProjectRole role) {
}
