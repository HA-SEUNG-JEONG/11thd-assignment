package com.example.collab.project;

import com.example.collab.common.CurrentUser;
import com.example.collab.project.dto.ProjectMemberAddRequest;
import com.example.collab.project.dto.ProjectMemberResponse;
import com.example.collab.project.dto.ProjectMemberRoleUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    /** 멤버 수는 상한이 작아 페이징하지 않는다. 목록 페이징 규칙은 프로젝트·작업에만 적용. */
    @GetMapping
    public List<ProjectMemberResponse> findAll(@PathVariable Long projectId,
                                               @CurrentUser Long userId) {
        return projectMemberService.findAll(projectId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse add(@PathVariable Long projectId,
                                     @CurrentUser Long userId,
                                     @Valid @RequestBody ProjectMemberAddRequest request) {
        return projectMemberService.add(projectId, userId, request);
    }

    @PatchMapping("/{targetUserId}")
    public ProjectMemberResponse changeRole(@PathVariable Long projectId,
                                            @PathVariable Long targetUserId,
                                            @CurrentUser Long userId,
                                            @Valid @RequestBody ProjectMemberRoleUpdateRequest request) {
        return projectMemberService.changeRole(projectId, userId, targetUserId, request);
    }

    @DeleteMapping("/{targetUserId}")
    public ResponseEntity<Void> remove(@PathVariable Long projectId,
                                       @PathVariable Long targetUserId,
                                       @CurrentUser Long userId) {
        projectMemberService.remove(projectId, userId, targetUserId);
        return ResponseEntity.noContent().build();
    }
}
