package com.example.collab.common;

import com.example.collab.common.exception.ConflictException;
import com.example.collab.common.exception.ForbiddenException;
import com.example.collab.common.exception.MissingUserIdException;
import com.example.collab.common.exception.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류 응답을 RFC 9457 {@link ProblemDetail} 한 형식으로 수렴시킨다.
 *
 * <p>{@code ResponseEntityExceptionHandler}를 상속하는 이유: Spring Boot의
 * {@code ProblemDetailsExceptionHandler}는 이 타입의 빈이 없을 때만 등록되므로,
 * 상속하면 검증 오류 처리를 이 클래스가 확실히 가져간다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** 실제로 겹친 트랜잭션에서 발생하는 낙관적 락 충돌. 애플리케이션 레벨 version 비교의 2차 방어선. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Resource was modified by another user");
    }

    @ExceptionHandler(MissingUserIdException.class)
    public ProblemDetail handleMissingUserId(MissingUserIdException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError -> errors.putIfAbsent(
                fieldError.getField(),
                fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage()));

        ProblemDetail body = e.getBody();
        body.setDetail("Request validation failed");
        body.setProperty("errors", errors);

        return handleExceptionInternal(e, body, headers, status, request);
    }
}
