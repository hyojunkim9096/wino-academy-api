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
 * 세션 유효성 필터 (통합 개선판)
 *
 * 1. 공개 경로(/register 등)는 즉시 통과
 * 2. 토큰이 유효한 경우:
 * - DB 세션 상태(ACTIVE/REVOKED/EXPIRED) 확인
 * - ACTIVE면 touch() 후 통과
 * - 아니면 에러 응답 (401/423) 후 중단
 * 3. 토큰이 없거나 무효한 경우:
 * - 강제 보호 경로(/me, /ping 등)면 401 에러
 * - 일반 경로는 통과 (Spring Security가 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionValidationFilter extends OncePerRequestFilter {

    private final SessionService sessionService;
    private final JwtProvider jwtProvider;

    // ✅ 공개 경로 (필터 프리패스)
    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth/login",
            "/api/auth/password-reset",
            "/api/admin/register",  // 회원가입
            "/api/admin/signUp",    // (구버전 호환)
            "/api/auth/logout-legacy"
    );

    // ✅ 토큰이 반드시 필요한 경로
    private static final Set<String> PROTECTED_AUTH_ENDPOINTS = Set.of(
            "/api/auth/me",
            "/api/auth/ping",
            "/api/auth/logout"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        final String uri = req.getRequestURI();
        final String method = req.getMethod();

        // 1. 공개 경로 & OPTIONS 요청은 무조건 통과
        if (isPublic(method, uri)) {
            chain.doFilter(req, res);
            return;
        }

        // 2. 토큰 유효성 검사
        String token = jwtProvider.resolveToken(req.getHeader(JwtProvider.AUTHORIZATION_HEADER));
        boolean hasValidToken = (token != null && jwtProvider.validateToken(token));

        // 3. 강제 보호 경로인데 토큰이 없거나 무효하면 -> 401 차단
        if (isProtectedAuth(uri) && !hasValidToken) {
            writeJson(res, HttpStatus.UNAUTHORIZED.value(), "SESSION_EXPIRED", "인증 정보가 없습니다.");
            return;
        }

        // 4. 토큰이 유효하면 -> DB 세션 상태 정밀 검사
        if (hasValidToken) {
            Claims claims = jwtProvider.getClaims(token);
            String sid = claims.get(JwtProvider.CLAIM_SID, String.class);

            // 세션 상태 조회 (예외 발생 시 만료로 취급)
            SessionService.SessionState state = safeStateOf(sid);

            if (state == SessionService.SessionState.ACTIVE) {
                // ✅ 정상: 활동 시간 갱신 후 진행
                try {
                    sessionService.touch(sid);
                } catch (Exception e) {
                    log.warn("Session touch failed: {}", e.getMessage());
                }
            } else {
                // ❌ 비정상: 차단 및 응답
                if (state == SessionService.SessionState.REVOKED) {
                    // 중복 로그인 등으로 강제 해지된 경우
                    writeJson(res, HttpStatus.LOCKED.value(), "SESSION_CONFLICT", "다른 기기에서 로그인되어 로그아웃되었습니다.");
                } else {
                    // 만료되거나 세션 정보 없음
                    writeJson(res, HttpStatus.UNAUTHORIZED.value(), "SESSION_EXPIRED", "세션이 만료되었습니다.");
                }
                return; // 체인 중단
            }
        }

        // 5. 그 외(토큰 없는 일반 요청)는 통과 -> Spring Security가 권한 처리
        chain.doFilter(req, res);
    }

    /* ================= 내부 유틸 ================= */

    private boolean isPublic(String method, String uri) {
        if ("OPTIONS".equalsIgnoreCase(method)) return true;
        if (uri == null) return false;
        // startsWith 로 하위 경로까지 허용
        for (String p : PUBLIC_PREFIXES) {
            if (uri.startsWith(p)) return true;
        }
        return false;
    }

    private boolean isProtectedAuth(String uri) {
        if (uri == null) return false;
        for (String p : PROTECTED_AUTH_ENDPOINTS) {
            if (uri.startsWith(p)) return true;
        }
        return false;
    }

    private SessionService.SessionState safeStateOf(String sid) {
        try {
            return sessionService.requireActiveState(sid);
        } catch (Exception e) {
            return SessionService.SessionState.EXPIRED;
        }
    }

    private void writeJson(HttpServletResponse res, int status, String code, String message) throws IOException {
        if (res.isCommitted()) return; // 이미 응답이 나갔으면 패스
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        // 헤더 추가 (프론트엔드 axios 인터셉터가 감지할 수 있도록)
        res.setHeader("X-Auth-Error", code);

        String body = "{\"error\":\"" + esc(code) + "\",\"message\":\"" + esc(message) + "\"}";
        res.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}