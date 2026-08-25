package com.example.collab.task.dto;

import com.example.collab.task.Task;
import com.example.collab.task.TaskStatus;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        Long assigneeId,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 담당자는 id만 노출한다 — 이름을 넣으면 LAZY 프록시가 초기화되어 목록에서 N+1이 난다. */
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getAssigneeId(),
                task.getVersion(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
