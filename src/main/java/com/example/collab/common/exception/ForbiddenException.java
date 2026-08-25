package com.example.collab.common.exception;

/** 경계 안에 있으나 역할이 부족한 경우. → 403 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
