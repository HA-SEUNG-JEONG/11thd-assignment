package com.example.collab.project;

/** 프로젝트 내 역할. 상하 관계가 아니라 열거된 집합으로만 비교한다(계층 없음). */
public enum ProjectRole {
    OWNER,
    ADMIN,
    MEMBER
}
