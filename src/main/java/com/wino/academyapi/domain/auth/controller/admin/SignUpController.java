// src/main/java/com/wino/academyapi/domain/auth/controller/admin/SignUpController.java
package com.wino.academyapi.domain.auth.controller.admin;

import com.wino.academyapi.domain.auth.dto.SignUpRequest;
import com.wino.academyapi.domain.auth.service.SignUpService;
import com.wino.academyapi.global.idempotency.IdempotencyService;
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
 * - 예외는 이 컨트롤러에서 캐치하여
 *   {code, message} JSON 포맷으로 응답.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class SignUpController {

    private final SignUpService signUpService;
    private final IdempotencyService idempotencyService;

    /**
     * 신규 등록
     * - ⚠️ @Valid 제거: DTO Bean Validation 예외(MethodArgumentNotValidException)가
     *   GlobalExceptionHandler로 튀어가는 것을 막고,
     *   모든 검증/에러를 Service + 여기 try/catch 에서 처리.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody SignUpRequest req,
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
            // DB 제약(중복 키 등) 또는 Service 에서 중복을 감지해 던진 경우
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            "CONFLICT",
                            e.getMessage() != null ? e.getMessage() : "이미 존재하거나 중복 제출이 감지되었습니다."
                    ));
        } catch (IllegalArgumentException e) {
            // 유효성 위반 등 비즈니스 에러
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(
                            "BAD_REQUEST",
                            e.getMessage() != null ? e.getMessage() : "요청 값이 올바르지 않습니다."
                    ));
        } catch (Exception e) {
            // 그 외는 500
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("INTERNAL_ERROR", "신규등록 처리 중 오류가 발생했습니다."));
        }
    }

    /** 과거 경로 호환 (/api/admin/signUp) */
    @PostMapping("/signUp")
    public ResponseEntity<?> signUpCompat(
            @RequestBody SignUpRequest req,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey
    ) {
        return register(req, idemKey);
    }

    /** 프런트에서 code/message로 쉽게 구분할 수 있도록 통일 */
    private record ErrorResponse(String code, String message) {}
}
