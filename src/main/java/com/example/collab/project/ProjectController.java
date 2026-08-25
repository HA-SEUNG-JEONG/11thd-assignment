package com.example.collab.project;

import com.example.collab.common.CurrentUser;
import com.example.collab.project.dto.ProjectCreateRequest;
import com.example.collab.project.dto.ProjectResponse;
import com.example.collab.project.dto.ProjectUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@CurrentUser Long userId,
                                                  @Valid @RequestBody ProjectCreateRequest request) {
        ProjectResponse response = projectService.create(userId, request);
        return ResponseEntity.created(URI.create("/api/projects/" + response.id())).body(response);
    }

    @GetMapping
    public Page<ProjectResponse> findMine(@CurrentUser Long userId, Pageable pageable) {
        return projectService.findMine(userId, pageable);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable Long projectId, @CurrentUser Long userId) {
        return projectService.get(projectId, userId);
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(@PathVariable Long projectId,
                                  @CurrentUser Long userId,
                                  @Valid @RequestBody ProjectUpdateRequest request) {
        return projectService.update(projectId, userId, request);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @CurrentUser Long userId) {
        projectService.delete(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
