// src/main/java/com/wino/academyapi/global/exception/GlobalExceptionHandler.java
// ============================================================================
// 전역 예외 처리기 (의존성 최소 버전)
//  - 표준 응답 바디: { code, message, path, timestamp }
//  - 상태 매핑(핵심):
//      * 400: IllegalArgumentException
//      * 403: AccessDeniedException
//      * 409: DataIntegrityViolationException, IllegalStateException
//      * RSE: ResponseStatusException 그대로
//      * 500: 그 외
// ============================================================================

package com.wino.academyapi.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private record ErrorBody(String code, String message, String path, String timestamp) {}

    private ErrorBody body(String code, String message, WebRequest req) {
        String path = req.getDescription(false).replace("uri=", "");
        return new ErrorBody(code, message, path, OffsetDateTime.now().toString());
    }

    private String codeOf(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case FORBIDDEN -> "FORBIDDEN";
            case CONFLICT -> "CONFLICT";
            case NOT_FOUND -> "NOT_FOUND";
            case UNPROCESSABLE_ENTITY -> "UNPROCESSABLE_ENTITY";
            default -> status.name();
        };
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleBad(IllegalArgumentException e, WebRequest req) {
        log.warn("[400] IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("BAD_REQUEST", e.getMessage(), req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleDenied(AccessDeniedException e, WebRequest req) {
        log.warn("[403] AccessDeniedException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("FORBIDDEN", "접근 권한이 없습니다.", req));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> handleDup(DataIntegrityViolationException e, WebRequest req) {
        log.warn("[409] DataIntegrityViolationException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("ALREADY_EXISTS", "이미 존재하는 값입니다.", req));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> handleConflict(IllegalStateException e, WebRequest req) {
        log.warn("[409] IllegalStateException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("CONFLICT", e.getMessage(), req));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleRSE(ResponseStatusException e, WebRequest req) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (status.is4xxClientError()) {
            log.warn("[{}] ResponseStatusException: {}", status.value(), e.getReason());
        } else {
            log.error("[{}] ResponseStatusException: {}", status.value(), e.getReason());
        }

        String code = codeOf(status);
        String msg  = (e.getReason() != null) ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(status).body(body(code, msg, req));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleEtc(Exception e, WebRequest req) {
        log.error("[500] Unhandled Exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "서버 오류가 발생했습니다.", req));
    }
}