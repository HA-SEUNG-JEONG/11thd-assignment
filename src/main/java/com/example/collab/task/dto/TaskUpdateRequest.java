package com.example.collab.task.dto;

import com.example.collab.task.TaskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 부분 수정. title·description·status·assigneeId는 nullable이며 null은 "미변경"을 뜻한다
 * — 담당자 해제는 v1 미지원.
 *
 * <p>{@code version}만 필수다. 클라이언트가 읽은 시점의 버전을 되돌려보내야
 * 트랜잭션 밖에서 벌어진 덮어쓰기를 감지할 수 있다.
 */
public record TaskUpdateRequest(
        String title,
        String description,
        TaskStatus status,
        Long assigneeId,
        @NotNull Long version) {}
