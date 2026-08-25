package com.example.collab.project.dto;

/** 부분 수정. 두 필드 모두 nullable이며 null은 "미변경"을 뜻한다. */
public record ProjectUpdateRequest(
        String name,
        String description) {
}
