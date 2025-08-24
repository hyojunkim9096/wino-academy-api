// src/main/java/com/wino/academyapi/global/web/ApiExceptionAdvice.java
package com.wino.academyapi.global.web;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionAdvice {

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(EntityNotFoundException e) {
        return Map.of("error", "NOT_FOUND", "message", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalStateException e) {
        return Map.of("error", "BAD_REQUEST", "message", e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(DataIntegrityViolationException e) {
        return Map.of("error", "CONFLICT",
                "message", "데이터 제약조건 위반(중복/무결성)입니다.");
    }
}
