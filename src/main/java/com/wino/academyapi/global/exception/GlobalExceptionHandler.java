// src/main/java/com/wino/academyapi/global/exception/GlobalExceptionHandler.java
// ============================================================================
// 전역 예외 처리기 (Standardized Error Handler)
// - 모든 컨트롤러에서 발생하는 예외를 잡아 표준 JSON 포맷으로 응답합니다.
// - @Valid 유효성 검사 실패(MethodArgumentNotValidException) 처리 포함
// ============================================================================
package com.wino.academyapi.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 표준 에러 응답 바디
    private record ErrorBody(String code, String message, String path, String timestamp) {}

    // 에러 바디 생성 헬퍼
    private ErrorBody body(String code, String message, WebRequest req) {
        String path = req.getDescription(false).replace("uri=", "");
        return new ErrorBody(code, message, path, OffsetDateTime.now().toString());
    }

    // HttpStatus -> String 코드 변환 헬퍼
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

    // ========================================================================
    // DTO 유효성 검사 실패 (@Valid / @Validated)
    // - 500 에러 대신 400 Bad Request로 처리하고, 첫 번째 에러 메시지를 반환합니다.
    // ========================================================================
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e, WebRequest req) {
        // 여러 필드 에러 중 첫 번째 메시지만 추출하여 사용자에게 전달
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");

        log.warn("[400] Validation Failed: {}", msg);
        return ResponseEntity.badRequest()
                .body(body("BAD_REQUEST", msg, req));
    }

    // 1. 잘못된 인자 (400)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleBad(IllegalArgumentException e, WebRequest req) {
        log.warn("[400] IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(body("BAD_REQUEST", e.getMessage(), req));
    }

    // 2. 권한 없음 (403)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleDenied(AccessDeniedException e, WebRequest req) {
        log.warn("[403] AccessDeniedException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("FORBIDDEN", "접근 권한이 없습니다.", req));
    }

    // 3. 데이터 무결성 위반 (409) - 중복 키 등
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorBody> handleDup(DataIntegrityViolationException e, WebRequest req) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "이미 존재하는 값입니다.";
        }
        log.warn("[409] DataIntegrityViolationException: {}", msg);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("CONFLICT", msg, req));
    }

    // 4. 비즈니스 로직 상태 충돌 (409)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorBody> handleConflict(IllegalStateException e, WebRequest req) {
        log.warn("[409] IllegalStateException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("CONFLICT", e.getMessage(), req));
    }

    // 5. ResponseStatusException 처리 (가변 상태)
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

    // 6. 그 외 모든 예외 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleEtc(Exception e, WebRequest req) {
        log.error("[500] Unhandled Exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "서버 오류가 발생했습니다.", req));
    }
}
