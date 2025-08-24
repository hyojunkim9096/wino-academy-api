// src/main/java/com/wino/academyapi/global/config/JpaAuditConfig.java
package com.wino.academyapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/** ✅ @CreatedBy/@LastModifiedBy 등에 현재 사용자 기록 */
@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {

    private static final int AUDITOR_MAX_LENGTH = 100;

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return Optional.of("system");
            }
            Object principal = auth.getPrincipal();

            if (principal instanceof UserDetails ud) {
                return Optional.of(sanitize(ud.getUsername()));
            }
            if (principal instanceof CharSequence cs) {
                String v = cs.toString();
                if (!isBlank(v)) return Optional.of(sanitize(v));
            }
            if (principal instanceof Map<?,?> claims) {
                String v = firstNonBlank(
                        asStr(claims.get("userId")),
                        asStr(claims.get("username")),
                        asStr(claims.get("loginId")),
                        asStr(claims.get("sub")),
                        asStr(claims.get("email"))
                );
                if (v != null) return Optional.of(sanitize(v));
            }
            String via = tryMethods(principal, "getUserId","getUsername","getLoginId","getEmail","getId");
            if (via != null) return Optional.of(sanitize(via));

            String name = auth.getName();
            if (!isBlank(name)) return Optional.of(sanitize(name));

            return Optional.of("system");
        };
    }

    private static String tryMethods(Object t, String... names) {
        if (t == null) return null;
        Class<?> c = t.getClass();
        for (String m : names) {
            try {
                Method mm = c.getMethod(m);
                Object v = mm.invoke(t);
                String s = asStr(v);
                if (s != null) return s;
            } catch (Exception ignore) {}
        }
        return null;
    }

    private static String asStr(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return isBlank(s) ? null : s;
    }
    private static boolean isBlank(String s){ return s==null || s.trim().isEmpty(); }
    private static String sanitize(String raw){
        if (raw == null) return "system";
        String s = raw.trim();
        if (s.isEmpty()) return "system";
        return (s.length() > AUDITOR_MAX_LENGTH) ? s.substring(0, AUDITOR_MAX_LENGTH) : s;
    }
    private static String firstNonBlank(String... arr){
        if (arr==null) return null;
        for (String s: arr) if (!isBlank(s)) return s;
        return null;
    }
}
