// src/main/java/com/wino/academyapi/global/audit/AdminActionLoggingFilter.java
package com.wino.academyapi.global.audit;

import com.wino.academyapi.domain.syslog.entity.AdminSystemLog;
import com.wino.academyapi.domain.syslog.service.AdminSystemLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;                  // ✅ 지연 주입으로 초기화 순환 방지
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;       // ✅ 스프링 표준 캐싱 래퍼 (별도 커스텀 클래스 불필요)
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * /api/admin/** 에 대한 "변경성 요청(POST/PUT/PATCH/DELETE)"을 시스템 로그로 적재하는 필터.
 *
 * 설계 포인트
 *  - 실패/성공과 무관하게 요청을 기록(체인 실행 후 finally 에서 저장)
 *  - 민감정보는 간단 마스킹 처리
 *  - 요청/응답 본문은 스프링의 ContentCaching*Wrapper 로 안전하게 캡처
 *  - JPA/DataSource 초기화 순환 방지를 위해 AdminSystemLogService 는 ObjectProvider 로 지연 획득
 *
 * 주의
 *  - 응답 래퍼를 썼다면 chain 후 반드시 copyBodyToResponse() 호출 (응답 바디 손실 방지)
 *  - 본문 캡처는 MAX_CAPTURE_BYTES 까지만 (DB/메모리 보호)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminActionLoggingFilter extends OncePerRequestFilter {

    /** 서비스는 직접 주입 대신 ObjectProvider로 지연 획득 (컨텍스트 초기화 순환 방지) */
    private final ObjectProvider<AdminSystemLogService> logServiceProvider;

    /** 로깅에 캡처할 요청 바디 최대 크기(바이트). 초과분은 잘라서 저장 */
    private static final int MAX_CAPTURE_BYTES = 50_000;

    /** 필터 적용 경로 프리픽스 */
    private static final String ADMIN_API_PREFIX = "/api/admin/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        final String uri = request.getRequestURI();
        final String m   = request.getMethod();
        if (uri == null || !uri.startsWith(ADMIN_API_PREFIX)) {
            return true; // 관리자 API가 아니면 미적용
        }
        // 변경 메서드만 기록
        return !(equalsAnyIgnoreCase(m, "POST", "PUT", "PATCH", "DELETE"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // ✅ 스프링 표준 캐싱 래퍼로 감싼다 (본문 재읽기 및 캡처 가능)
        ContentCachingRequestWrapper req  = new ContentCachingRequestWrapper(request, MAX_CAPTURE_BYTES);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);

        long startedAt = System.currentTimeMillis();
        String actor   = resolveActor();
        int status     = 0;
        Exception chainError = null;

        try {
            chain.doFilter(req, res);
        } catch (Exception ex) {
            chainError = ex;
            throw ex;
        } finally {
            try {
                status = safeStatus(res);
                String body = extractRequestBody(req);
                String maskedBody = maskSensitive(body);

                AdminSystemLogService svc = safeGet(logServiceProvider);
                if (svc != null) {
                    AdminSystemLog logRow = AdminSystemLog.builder()
                            .createdAt(LocalDateTime.now())                          // created_at 보장
                            .actorUserId(actor)
                            .method(req.getMethod())
                            .path(req.getRequestURI())                              // 쿼리스트링 제외(URI만)
                            .ipAddress(resolveClientIp(req))
                            .userAgent(req.getHeader("User-Agent"))
                            .requestBody(maskedBody)
                            .statusCode(status)                                     // 응답 코드
                            .elapsedMs(System.currentTimeMillis() - startedAt)      // 소요 시간
                            .errorMessage(chainError != null ? shortMsg(chainError) : null)
                            .build();
                    svc.save(logRow);                                               // 화면명/코드는 서비스에서 자동 보강
                }
            } catch (Exception logEx) {
                // 로깅 실패는 본 처리에 영향 주지 않음
                log.debug("[AdminActionLogging] save skipped: {}", logEx.toString());
            } finally {
                // ✅ 응답 바디를 원래 스트림으로 복사 (안 하면 클라이언트가 바디를 못 받음)
                try { res.copyBodyToResponse(); } catch (Exception ignore) {}
            }
        }
    }

    // ───────────────────────── 내부 유틸 ─────────────────────────

    /** 현재 인증 주체 이름(없으면 "anonymous") */
    private String resolveActor() {
        try {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null) {
                String name = a.getName();
                if (name != null && !name.isBlank()) return name;
            }
        } catch (Exception ignore) {}
        return "anonymous";
    }

    /** 가장 신뢰할 수 있는 클라이언트 IP 추출 (프록시/로드밸런서 고려) */
    private String resolveClientIp(HttpServletRequest req) {
        // X-Forwarded-For: client, proxy1, proxy2 ... → 첫 번째가 원 IP
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > -1) ? xff.substring(0, comma).trim() : xff.trim();
        }
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return req.getRemoteAddr();
    }

    /** 요청 본문 추출(캐시에서). 최대 MAX_CAPTURE_BYTES 까지만 저장 */
    private String extractRequestBody(ContentCachingRequestWrapper req) {
        byte[] buf = req.getContentAsByteArray();
        if (buf == null || buf.length == 0) return null;

        int len = Math.min(buf.length, MAX_CAPTURE_BYTES);
        // 요청 인코딩 추정 (없으면 UTF-8)
        Charset cs = StandardCharsets.UTF_8;
        try {
            String enc = req.getCharacterEncoding();
            if (enc != null && !enc.isBlank()) cs = Charset.forName(enc);
        } catch (Exception ignore) {}
        return new String(buf, 0, len, cs);
    }

    /** 민감 키 간단 마스킹(JSON/폼 공용) */
    private String maskSensitive(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        return raw
                // JSON 키 패턴
                .replaceAll("(?i)\"password\"\\s*:\\s*\".*?\"", "\"password\":\"***\"")
                .replaceAll("(?i)\"newPassword\"\\s*:\\s*\".*?\"", "\"newPassword\":\"***\"")
                .replaceAll("(?i)\"encPassword\"\\s*:\\s*\".*?\"", "\"encPassword\":\"***\"")
                .replaceAll("(?i)\"passwordPlain\"\\s*:\\s*\".*?\"", "\"passwordPlain\":\"***\"")
                // 폼/쿼리스트링 스타일도 일부 커버 (password=...& / &newPassword=...)
                .replaceAll("(?i)(^|[&?])password=[^&\\r\\n]*", "$1password=***")
                .replaceAll("(?i)(^|[&?])newPassword=[^&\\r\\n]*", "$1newPassword=***");
    }

    /** 응답 상태코드 안전 조회 */
    private int safeStatus(HttpServletResponse resp) {
        try { return resp.getStatus(); }
        catch (Exception ignore) { return 0; }
    }

    /** 예외 메시지 한 줄 요약 */
    private String shortMsg(Exception e) {
        String m = e.getMessage();
        return (m == null || m.length() > 500) ? (e.getClass().getSimpleName()) : m;
    }

    /** 지연 주입 안전 획득 */
    private static AdminSystemLogService safeGet(ObjectProvider<AdminSystemLogService> p) {
        try { return p.getIfAvailable(); }
        catch (Exception e) { return null; }
    }

    /** equalsIgnoreCase 다중 비교 */
    private static boolean equalsAnyIgnoreCase(String s, String... cands) {
        if (s == null) return false;
        for (String c : cands) {
            if (s.equalsIgnoreCase(c)) return true;
        }
        return false;
    }
}
