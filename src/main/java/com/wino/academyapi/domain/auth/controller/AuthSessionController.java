// src/main/java/com/wino/academyapi/domain/auth/controller/AuthSessionController.java
package com.wino.academyapi.domain.auth.controller;

import com.wino.academyapi.domain.admin.staff.entity.AdminUser;
import com.wino.academyapi.domain.admin.staff.repository.AdminUserRepository;
import com.wino.academyapi.domain.auth.service.SessionService;
import com.wino.academyapi.global.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * /api/auth/me, /api/auth/ping, /api/auth/logout
 * - 항상 JWT sid 기준으로 세션 상태 동기화/검증
 * - 프런트는 /api/auth/me.id 를 X-App-User-Id 헤더로 넘길 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthSessionController {

    private final SessionService sessionService;
    private final AdminUserRepository userRepo;
    private final JwtProvider jwtProvider;

    /** 현재 사용자/세션 상태 */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest req) {
        Claims claims = requireClaims(req);
        String userId = claims.getSubject();
        String sid = claims.get("sid", String.class);

        AdminUser u = userRepo.findByUserId(userId).orElseThrow();

        long remaining = 0L;
        if (sid != null && !sid.isBlank()) {
            // 세션이 비정상이어도 예외 대신 0 처리 (서비스에 해당 메서드 추가됨)
            remaining = sessionService.remainingSeconds(sid);
        }
        LocalDateTime exp = LocalDateTime.now().plusSeconds(Math.max(0, remaining));

        Map<String, Object> body = new HashMap<>();
        body.put("id", u.getId());                // ✅ 프런트 공통헤더용 (X-App-User-Id)
        body.put("userId", u.getUserId());
        body.put("userName", u.getUserName());
        body.put("remainingSeconds", remaining);
        body.put("sessionExpiresAt", toEpochMillis(exp));
        return ResponseEntity.ok(body);
    }

    /** 활동 핑: 만료 시간을 현재로부터 session.minutes로 즉시 초기화 */
    @PostMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(HttpServletRequest req) {
        Claims claims = requireClaims(req);
        String sid = claims.get("sid", String.class);
        if (sid == null || sid.isBlank()) {
            throw new IllegalStateException("세션 식별자가 없습니다.");
        }

        LocalDateTime newExp = sessionService.touch(sid);
        long remaining = Duration.between(LocalDateTime.now(), newExp).getSeconds();

        Map<String, Object> body = new HashMap<>();
        body.put("remainingSeconds", Math.max(0, remaining));
        body.put("sessionExpiresAt", toEpochMillis(newExp));
        return ResponseEntity.ok(body);
    }

    /** 로그아웃(해지) — 멱등/조용히 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        Claims claims = requireClaims(req);
        String sid = claims.get("sid", String.class);
        if (sid != null && !sid.isBlank()) {
            sessionService.revoke(sid);
        }
        return ResponseEntity.ok().build();
    }

    // ==== 내부 유틸 ====
    private Claims requireClaims(HttpServletRequest req) {
        String h = req.getHeader(JwtProvider.AUTHORIZATION_HEADER);
        if (h == null) h = req.getHeader("Authorization");
        String token = jwtProvider.resolveToken(h);
        if (token == null || !jwtProvider.validateToken(token)) {
            throw new IllegalStateException("인증 토큰이 유효하지 않습니다.");
        }
        return jwtProvider.getClaims(token);
    }

    private long toEpochMillis(LocalDateTime dt) {
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}