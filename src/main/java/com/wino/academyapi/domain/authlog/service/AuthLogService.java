// src/main/java/com/wino/academyapi/domain/authlog/service/AuthLogService.java
package com.wino.academyapi.domain.authlog.service;

import com.wino.academyapi.domain.authlog.entity.AdminAuthLog;
import com.wino.academyapi.domain.authlog.model.AdminAuthEventType;
import com.wino.academyapi.domain.authlog.repository.AdminAuthLogRepository;
import com.wino.academyapi.global.web.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 로그 저장 서비스
 * - 실패 흐름에서도 롤백되지 않도록 REQUIRES_NEW
 */
@Service
@RequiredArgsConstructor
public class AuthLogService {

    private final AdminAuthLogRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loginSuccess(Long adminId, String userId, HttpServletRequest req, String reason) {
        saveLog(adminId, userId, AdminAuthEventType.LOGIN_SUCCESS, true, reason, req, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loginFailure(Long adminIdOrNull, String userId, HttpServletRequest req, String reason) {
        saveLog(adminIdOrNull, userId, AdminAuthEventType.LOGIN_FAILURE, false, reason, req, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logout(Long adminIdOrNull, String userId, HttpServletRequest req, String reason) {
        saveLog(adminIdOrNull, userId, AdminAuthEventType.LOGOUT, true, reason, req, null);
    }

    /** ★ JWT sid를 직접 넣고 싶을 때 쓰는 오버로드 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logoutWithSid(Long adminIdOrNull, String userId, String sid, HttpServletRequest req, String reason) {
        saveLog(adminIdOrNull, userId, AdminAuthEventType.LOGOUT, true, reason, req, sid);
    }

    private void saveLog(Long adminId, String userId, AdminAuthEventType type, boolean success,
                         String reason, HttpServletRequest req, String jwtSidOrNull) {
        String httpSessionId = RequestUtils.sessionId(req);
        String sessionIdForLog = (jwtSidOrNull != null && !jwtSidOrNull.isBlank())
                ? jwtSidOrNull : (httpSessionId != null ? httpSessionId : "");

        AdminAuthLog log = AdminAuthLog.builder()
                .adminId(adminId)
                .userId(userId != null ? userId : "")
                .eventType(type)
                .success(success)
                .reason(reason)
                .ipAddress(RequestUtils.clientIp(req))
                .userAgent(RequestUtils.userAgent(req))
                .sessionId(sessionIdForLog) // ← JWT sid가 있으면 그걸로 기록
                .build();
        repo.save(log);
    }
}
