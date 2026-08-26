package com.example.collab.task;

import com.example.collab.common.CurrentUser;
import com.example.collab.task.dto.TaskCreateRequest;
import com.example.collab.task.dto.TaskResponse;
import com.example.collab.task.dto.TaskUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> create(@PathVariable Long projectId,
                                               @CurrentUser Long userId,
                                               @Valid @RequestBody TaskCreateRequest request) {
        TaskResponse response = taskService.create(projectId, userId, request);
        return ResponseEntity
                .created(URI.create("/api/projects/" + projectId + "/tasks/" + response.id()))
                .body(response);
    }

    @GetMapping
    public Page<TaskResponse> search(@PathVariable Long projectId,
                                     @CurrentUser Long userId,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) TaskStatus status,
                                     @ParameterObject Pageable pageable) {
        return taskService.search(projectId, userId, keyword, status, pageable);
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(@PathVariable Long projectId,
                            @PathVariable Long taskId,
                            @CurrentUser Long userId) {
        return taskService.get(projectId, userId, taskId);
    }

    @PatchMapping("/{taskId}")
    public TaskResponse update(@PathVariable Long projectId,
                               @PathVariable Long taskId,
                               @CurrentUser Long userId,
                               @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.update(projectId, userId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId,
                                       @PathVariable Long taskId,
                                       @CurrentUser Long userId,
                                       @RequestParam Long version) {
        taskService.delete(projectId, userId, taskId, version);
        return ResponseEntity.noContent().build();
    }
}
