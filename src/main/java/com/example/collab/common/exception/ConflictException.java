package com.example.collab.common.exception;

/** 리소스의 현재 상태와 충돌하는 요청. → 409 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
