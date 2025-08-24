// src/main/java/com/wino/academyapi/global/web/RequestUtils.java
package com.wino.academyapi.global.web;

import jakarta.servlet.http.HttpServletRequest;

public class RequestUtils {

    /** 프록시 환경(X-Forwarded-For) 고려 IP 추출 */
    public static String clientIp(HttpServletRequest req) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "CF-Connecting-IP",
                "True-Client-IP"
        };
        for (String h : headers) {
            String v = req.getHeader(h);
            if (v != null && !v.isBlank()) {
                // X-Forwarded-For: client, proxy1, proxy2...
                return v.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return ua != null ? ua : "";
    }

    public static String sessionId(HttpServletRequest req) {
        // JWT 무상태면 세션이 없을 수 있음
        return req.getRequestedSessionId();
    }
}
