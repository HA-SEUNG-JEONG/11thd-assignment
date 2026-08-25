package com.example.collab.task.dto;

import com.example.collab.task.TaskStatus;

/** 부분 수정. 전 필드 nullable이며 null은 "미변경"을 뜻한다 — 담당자 해제는 v1 미지원. */
public record TaskUpdateRequest(
        String title, String description, TaskStatus status, Long assigneeId) {}
