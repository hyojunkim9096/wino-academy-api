// src/main/java/com/wino/academyapi/global/exception/GlobalExceptionHandler.java
package com.wino.academyapi.global.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;

/**
 * 전역 예외 처리
 * - 검증 오류: 400
 * - 중복/상태 충돌: 409
 * - 권한 없음: 403
 * - 기타: 500
 * 응답 포맷: { code, message, path, timestamp }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private record ErrorBody(String code, String message, String path, String timestamp) {}

    private ErrorBody body(String code, String message, WebRequest req) {
        String path = req.getDescription(false).replace("uri=", "");
        return new ErrorBody(code, message, path, OffsetDateTime.now().toString());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e, WebRequest req) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("VALIDATION_ERROR", msg, req));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> handleDup(DataIntegrityViolationException e, WebRequest req) {
        // DB 상세 메시지는 숨기고 사용자용 메시지로 표준화
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("ALREADY_EXISTS", "이미 존재하는 값입니다.", req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleBad(IllegalArgumentException e, WebRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("BAD_REQUEST", e.getMessage(), req));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> handleConflict(IllegalStateException e, WebRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("CONFLICT", e.getMessage(), req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleDenied(AccessDeniedException e, WebRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("FORBIDDEN", "접근 권한이 없습니다.", req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleEtc(Exception e, WebRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body("INTERNAL_ERROR", "서버 오류가 발생했습니다.", req));
    }
}
