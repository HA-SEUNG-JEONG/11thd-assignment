package com.example.collab.task.dto;

import com.example.collab.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;

/** status가 null이면 TODO로 시작한다. assigneeId가 null이면 담당자 없음. */
public record TaskCreateRequest(
        @NotBlank String title, String description, TaskStatus status, Long assigneeId) {}
