// src/main/java/com/wino/academyapi/global/security/SessionValidationFilter.java
package com.wino.academyapi.global.security;

import com.wino.academyapi.domain.auth.service.SessionService;
import com.wino.academyapi.global.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 세션 유효성 필터
 *
 * - ✅ 공개 경로(로그인/비번재설정/신규등록/OPTIONS)는 완전 패스
 * - ✅ 토큰 없는 요청/무효 토큰은 개입하지 않고 체인 통과(최종 인가는 Security에서 판단)
 * - ✅ 유효 토큰이면 DB 세션 상태 검사:
 *     REVOKED → 423 (X-Auth-Error: SESSION_CONFLICT)
 *     EXPIRED/NOT_FOUND → 401 (X-Auth-Error: SESSION_EXPIRED)
 *     ACTIVE → 체인 계속 + 🆕 여기서 즉시 touch(sid) 호출해 세션 만료시각을 연장
 *        → 결과적으로 "다른 동작"을 해도 남은시간이 60분으로 계속 유지됨
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionValidationFilter extends OncePerRequestFilter {

    // 공개 엔드포인트 prefix들 (그대로 유지)
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/password-reset", // 하위 전체 허용
            "/api/admin/register",
            "/api/admin/signUp"
    );

    private final SessionService sessionService;
    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        final String uri = req.getRequestURI();

        // 0) 공개 경로/OPTIONS → 완전 패스
        if (isPublic(req.getMethod(), uri)) {
            chain.doFilter(req, res);
            return;
        }

        // 1) 토큰 추출 — 없으면 개입 안 함
        String token = jwtProvider.resolveToken(req.getHeader(JwtProvider.AUTHORIZATION_HEADER));
        if (token == null) {
            chain.doFilter(req, res);
            return;
        }

        // 2) 토큰 검증 실패 — 개입 안 함(시큐리티에서 인가 판단)
        if (!jwtProvider.validateToken(token)) {
            chain.doFilter(req, res);
            return;
        }

        // 3) 유효 토큰 → DB 세션 상태 점검
        final Claims claims = jwtProvider.getClaims(token);
        final String sid = claims.get(JwtProvider.CLAIM_SID, String.class);

        SessionService.SessionState state;
        try {
            state = sessionService.requireActiveState(sid);
        } catch (Exception e) {
            // 세션 정보 자체 조회 불가/예외 → 만료 처리
            writeJson(res, HttpStatus.UNAUTHORIZED.value(), "SESSION_EXPIRED", "세션이 만료되었거나 해제되었습니다.");
            res.setHeader("X-Auth-Error", "SESSION_EXPIRED");
            return;
        }

        switch (state) {
            case ACTIVE -> {
                // 🆕 핵심: 어떤 인증된 요청이든 들어오면 세션을 즉시 연장.
                // session.minutes(예: 60) 기준으로 expires_at을 now+60분으로 갱신.
                try {
                    sessionService.touch(sid);
                } catch (Exception e) {
                    // 터치 실패가 있어도 사용성을 위해 요청은 계속 진행(로그만 남김)
                    log.warn("Session touch failed for sid={}", sid, e);
                }
                chain.doFilter(req, res);
            }
            case REVOKED -> {
                writeJson(res, HttpStatus.LOCKED.value(), "SESSION_CONFLICT", "다른 장소에서 로그인하여 로그아웃 되었습니다.");
                res.setHeader("X-Auth-Error", "SESSION_CONFLICT");
            }
            case EXPIRED, NOT_FOUND -> {
                writeJson(res, HttpStatus.UNAUTHORIZED.value(), "SESSION_EXPIRED", "세션이 만료되었거나 해제되었습니다.");
                res.setHeader("X-Auth-Error", "SESSION_EXPIRED");
            }
        }
    }

    private boolean isPublic(String method, String uri) {
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        if (uri == null) return false;
        if (uri.startsWith("/api/auth/password-reset")) return true; // 하위 경로 전체 허용
        for (String p : PUBLIC_PREFIXES) {
            if (uri.startsWith(p)) return true;
        }
        return false;
    }

    private void writeJson(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        String body = "{\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}";
        res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
