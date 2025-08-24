// src/main/java/com/wino/academyapi/domain/auth/controller/PasswordResetController.java
package com.wino.academyapi.domain.auth.controller;

import com.wino.academyapi.domain.auth.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 비밀번호 재설정(공개 API) */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /** 코드 요청 */
    @PostMapping("/request")
    public ResponseEntity<Void> request(@RequestBody ResetRequest req) {
        passwordResetService.requestCode(req.userId());
        return ResponseEntity.ok().build();
    }

    /** 코드 확인 + 비밀번호 변경 */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestBody ConfirmRequest req) {
        passwordResetService.resetPassword(req.userId(), req.code(), req.newPassword());
        return ResponseEntity.ok().build();
    }

    // 프런트 압축본 기준 필드명(userId/code/newPassword) 그대로 사용
    public record ResetRequest(String userId) {}
    public record ConfirmRequest(String userId, String code, String newPassword) {}
}
