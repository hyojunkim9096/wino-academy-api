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
 * 핵심 동작
 * - ✅ 공개 경로(로그인/비번재설정/신규등록/OPTIONS/레거시 로그아웃)는 완전 패스
 * - ✅ "강제 보호" 경로(/api/auth/me, /api/auth/ping, /api/auth/logout)는
 *      · 토큰 없거나 무효 → 401 (SESSION_EXPIRED)
 *      · 세션 REVOKED → 423 (SESSION_CONFLICT)
 *      · 세션 EXPIRED/NOT_FOUND → 401 (SESSION_EXPIRED)
 *      · 세션 ACTIVE → touch(sid)로 슬라이딩 만료 연장 후 체인 진행
 * - ✅ 그 외 경로:
 *      · 토큰 없거나 무효 → 필터는 개입하지 않음(최종 인가는 Security에서 판단)
 *      · 토큰 유효 → 세션 상태 점검 및 touch 동작 동일
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionValidationFilter extends OncePerRequestFilter {

    /** 공개 엔드포인트 prefix들 — 하위 전체 허용 */
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/password-reset", // 하위 전체 허용
            "/api/admin/register",
            "/api/admin/signUp",
            "/api/auth/logout-legacy"
    );

    /** 보안상 반드시 보호해야 하는 엔드포인트(잘못된 보안규칙에도 불구하고 강제 보호) */
    private static final Set<String> PROTECTED_AUTH_ENDPOINTS = Set.of(
            "/api/auth/me",
            "/api/auth/ping",
            "/api/auth/logout" // 레거시가 아닌 정식 로그아웃은 보호
    );

    private final SessionService sessionService;
    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        final String uri = req.getRequestURI();
        final String method = req.getMethod();

        // 0) 공개 경로/OPTIONS → 완전 패스
        if (isPublic(method, uri)) {
            chain.doFilter(req, res);
            return;
        }

        // Authorization 헤더에서 Bearer 토큰 추출(순수 jwt)
        final String token = jwtProvider.resolveToken(req.getHeader(JwtProvider.AUTHORIZATION_HEADER));

        // 1) 강제 보호 경로: 토큰 필수
        if (isProtectedAuth(uri)) {
            // 토큰 없거나 유효하지 않으면 401
            if (token == null || !jwtProvider.validateToken(token)) {
                writeJson(res, HttpStatus.UNAUTHORIZED.value(), "SESSION_EXPIRED", "세션이 만료되었거나 해제되었습니다.");
                res.setHeader("X-Auth-Error", "SESSION_EXPIRED");
                return;
            }
            // 토큰 유효 → 세션 상태 점검
            final Claims claims = jwtProvider.getClaims(token);
            final String sid = claims.get(JwtProvider.CLAIM_SID, String.class);

            switch (safeStateOf(sid)) {
                case ACTIVE -> {
                    // 슬라이딩 만료 연장
                    try { sessionService.touch(sid); } catch (Exception e) {
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
            return;
        }

        // 2) 일반 경로: 토큰 없거나 무효 → 개입하지 않고 체인 계속(인가 판단은 Security)
        if (token == null || !jwtProvider.validateToken(token)) {
            chain.doFilter(req, res);
            return;
        }

        // 3) 일반 경로 + 유효 토큰 → 세션 상태 점검(+touch) 후 진행
        final Claims claims = jwtProvider.getClaims(token);
        final String sid = claims.get(JwtProvider.CLAIM_SID, String.class);

        switch (safeStateOf(sid)) {
            case ACTIVE -> {
                try { sessionService.touch(sid); } catch (Exception e) {
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

    /** 공개 경로/OPTIONS 판정 */
    private boolean isPublic(String method, String uri) {
        if (method != null && "OPTIONS".equalsIgnoreCase(method)) return true;
        if (uri == null) return false;
        for (String p : PUBLIC_PREFIXES) {
            if (uri.startsWith(p)) return true;
        }
        return false;
    }

    /** 강제 보호 경로 판정 */
    private boolean isProtectedAuth(String uri) {
        if (uri == null) return false;
        for (String p : PROTECTED_AUTH_ENDPOINTS) {
            if (uri.startsWith(p)) return true;
        }
        return false;
    }

    /** 세션 상태 안전 조회(예외를 만료로 변환) */
    private SessionService.SessionState safeStateOf(String sid) {
        try {
            return sessionService.requireActiveState(sid);
        } catch (Exception e) {
            return SessionService.SessionState.EXPIRED;
        }
    }

    /** 간단 JSON 에러 응답 */
    private void writeJson(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        String body = "{\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}";
        res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    /** JSON string escape 최소 구현 */
    private String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}