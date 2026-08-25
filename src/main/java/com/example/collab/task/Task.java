package com.example.collab.task;

import com.example.collab.common.BaseTimeEntity;
import com.example.collab.project.Project;
import com.example.collab.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "tasks",
        indexes = @Index(name = "idx_tasks_project_status", columnList = "project_id, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /**
     * 진짜로 겹친 트랜잭션에 대한 2차 방어선. 과제가 말한 충돌(트랜잭션 밖 read-modify-write)은
     * 이 애노테이션만으로는 잡히지 않는다 — 서비스의 명시 비교가 본체다.
     */
    @Version
    private Long version;

    public Task(Project project, String title, String description, TaskStatus status, User assignee) {
        this.project = project;
        this.title = title;
        this.description = description;
        this.status = status;
        this.assignee = assignee;
    }

    /**
     * 부분 수정. null은 "미변경"을 뜻한다.
     *
     * <p>따라서 담당자 해제는 v1에서 지원하지 않는다 — 교체만 가능하다.
     * PATCH의 null 규칙을 도메인 전체에서 하나로 유지하기 위한 의도적 제약.
     */
    public void update(String title, String description, TaskStatus status, User assignee) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (status != null) {
            this.status = status;
        }
        if (assignee != null) {
            this.assignee = assignee;
        }
    }

    /** 멤버 제거 시 담당자를 비운다. update()의 "null = 미변경" 규칙과 섞이지 않게 별도 메서드로 둔다. */
    public void unassign() {
        this.assignee = null;
    }

    /**
     * LAZY 프록시의 식별자만 읽는다 — 프록시가 초기화되지 않으므로 목록 응답에서 N+1이 나지 않는다.
     * 응답 DTO는 이 값만 쓰고 담당자 이름은 노출하지 않는다.
     */
    public Long getAssigneeId() {
        return assignee == null ? null : assignee.getId();
    }
}
