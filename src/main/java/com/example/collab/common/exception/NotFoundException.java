package com.example.collab.common.exception;

/** 리소스가 없거나, 요청자에게 존재를 숨겨야 하는 경우. → 404 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
