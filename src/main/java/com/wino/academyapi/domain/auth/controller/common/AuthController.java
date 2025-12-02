// src/main/java/com/wino/academyapi/domain/auth/controller/common/AuthController.java
package com.wino.academyapi.domain.auth.controller.common;

import com.wino.academyapi.domain.auth.dto.AuthRequest;
import com.wino.academyapi.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 로그인 컨트롤러 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody AuthRequest.Login reqDto,
                                                     HttpServletRequest req) {
        String token = authService.login(reqDto, req);
        return ResponseEntity.ok(Map.of("token", token));
    }

    /** 잠금 등 비즈니스 제약 → 423 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> illegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .header("X-Auth-Error", "ACCOUNT_LOCKED")
                .body(Map.of("error", "ACCOUNT_LOCKED", "message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unknown(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR", "message", "로그인 처리 중 오류가 발생했습니다."));
    }
}
