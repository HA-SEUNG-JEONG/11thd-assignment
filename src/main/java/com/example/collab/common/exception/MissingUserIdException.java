package com.example.collab.common.exception;

/**
 * X-User-Id 헤더가 없거나 수치가 아닌 경우. → 400
 *
 * <p>{@code MissingRequestHeaderException}은 {@code @RequestHeader} 파라미터에서만 발생하므로,
 * 커스텀 ArgumentResolver는 이 예외를 직접 던져야 400이 나간다.
 */
public class MissingUserIdException extends RuntimeException {

    public MissingUserIdException(String message) {
        super(message);
    }
}
