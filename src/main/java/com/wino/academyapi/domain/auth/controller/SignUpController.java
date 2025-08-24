// src/main/java/com/wino/academyapi/domain/auth/controller/SignUpController.java
package com.wino.academyapi.domain.auth.controller;

import com.wino.academyapi.domain.auth.dto.SignUpRequest;
import com.wino.academyapi.domain.auth.service.SignUpService;
import com.wino.academyapi.global.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 신규등록(공개 API)
 * - 서비스 register(..)는 void → 반환값 없음
 * - 멱등키(X-Idempotency-Key)가 전달된 경우에만 checkAndMark 로 중복 제출 방지
 *   (키가 없으면 멱등성 검사는 스킵하고 정상 처리)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class SignUpController {

    private final SignUpService signUpService;
    private final IdempotencyService idempotencyService;

    /**
     * 신규 등록
     * 요청 헤더에 X-Idempotency-Key 가 오면, 동일 키의 재요청은 409로 차단합니다.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody @Valid SignUpRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey
    ) {
        try {
            // ✅ 멱등키가 있는 경우에만 중복 검사 (없으면 검사 스킵)
            if (StringUtils.hasText(idemKey)) {
                boolean firstTime = idempotencyService.checkAndMark(idemKey);
                if (!firstTime) {
                    // 동일 키로 이미 처리됨 → 409
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new ErrorResponse("CONFLICT", "이미 처리된 요청입니다."));
                }
            }

            // 실제 등록 처리 (반환값 없음)
            signUpService.register(req);

            // 프런트는 본문을 사용하지 않으므로 201만 내려도 충분
            return ResponseEntity.status(HttpStatus.CREATED).build();

        } catch (DataIntegrityViolationException e) {
            // DB 제약(중복 키 등)
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("CONFLICT", "이미 존재하거나 중복 제출이 감지되었습니다."));
        } catch (IllegalArgumentException e) {
            // 유효성 위반 등 비즈니스 에러
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
        } catch (Exception e) {
            // 그 외는 500
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "신규등록 처리 중 오류가 발생했습니다."));
        }
    }

    /** 과거 경로 호환 (/api/admin/signUp) */
    @PostMapping("/signUp")
    public ResponseEntity<?> signUpCompat(
            @RequestBody @Valid SignUpRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey
    ) {
        return register(req, idemKey);
    }

    /** 프런트에서 code/message로 쉽게 구분할 수 있도록 통일 */
    private record ErrorResponse(String code, String message) {}
}
