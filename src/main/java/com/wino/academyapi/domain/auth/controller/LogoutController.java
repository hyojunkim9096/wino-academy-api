// src/main/java/com/wino/academyapi/domain/auth/controller/LogoutController.java
package com.wino.academyapi.domain.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wino.academyapi.domain.authlog.service.AuthLogService;
import com.wino.academyapi.global.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LogoutController {

    private final JwtProvider jwtProvider;      // 검증이 필요하면 사용 (여기서는 파싱만 보조)
    private final AuthLogService authLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/logout-legacy")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        String userId = resolveUserIdForLog(req);
        // adminId는 별도 토큰 클레임 없으면 null로
        Long adminId = null;

        // 로깅(무상태 환경: 서버 세션 정리는 없음)
        authLogService.logout(adminId, userId, req, null);
        return ResponseEntity.ok().build();
    }

    /** 로깅용 userId 추출: 1) SecurityContext → 2) JWT payload decode (sub/userId/username) */
    private String resolveUserIdForLog(HttpServletRequest req) {
        // 1) SecurityContext 에서 먼저 시도
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        // 2) Authorization 헤더에서 Bearer 토큰 추출 후 payload decode
        String token = bearerToken(req);
        if (token == null) return "";
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return "";
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(payloadJson);
            // 우선순위: sub → userId → username
            if (node.hasNonNull("sub")) return node.get("sub").asText("");
            if (node.hasNonNull("userId")) return node.get("userId").asText("");
            if (node.hasNonNull("username")) return node.get("username").asText("");
        } catch (Exception ignored) {}
        return "";
    }

    private String bearerToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return (h != null && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
