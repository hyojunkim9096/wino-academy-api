package com.wino.academyapi.global.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 모든 요청에서 X-App-User-Id / X-App-Event-Note 헤더를 읽어
 * ThreadLocal(AppUserContext)에 저장한다.
 *
 * ⚠️ 여기서는 DB 세션 변수(@app_user_id/@event_note)를 직접 세팅하지 않는다.
 *    커넥션이 다를 수 있으므로, @Transactional 서비스에서 DbSessionVars로 주입해야 한다.
 */
@Component
public class AppUserSqlVarInterceptor implements HandlerInterceptor {

    private static Long parseLongSafe(String s) {
        if (s == null || s.isBlank()) return 0L;
        try { return Long.parseLong(s.trim()); } catch (Exception ignored) { return 0L; }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long uid = parseLongSafe(request.getHeader("X-App-User-Id"));
        String note = request.getHeader("X-App-Event-Note"); // 선택

        // 로그 상관관계
        MDC.put("app_user_id", String.valueOf(uid));

        // 요청 ThreadLocal 컨텍스트에 저장 (트랜잭션 진입 시 DbSessionVars가 사용)
        AppUserContext.setUserId(uid);
        AppUserContext.setNote(note);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // ThreadLocal 정리
        AppUserContext.clear();
        MDC.remove("app_user_id");
    }
}