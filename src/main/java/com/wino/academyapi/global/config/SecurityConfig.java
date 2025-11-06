// src/main/java/com/wino/academyapi/global/config/SecurityConfig.java
package com.wino.academyapi.global.config;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import com.wino.academyapi.domain.cors.service.CorsSettingService;
import com.wino.academyapi.domain.securityrule.entity.SecurityRule;
import com.wino.academyapi.domain.securityrule.entity.SecurityRuleAccessType;
import com.wino.academyapi.domain.securityrule.service.SecurityRuleService;
import com.wino.academyapi.global.jwt.JwtAuthenticationFilter;
import com.wino.academyapi.global.security.SessionValidationFilter;  // ✅ 추가
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Collections;
import java.util.List;

/**
 * 동적 보안규칙(SecurityRule) 기반 Spring Security 설정.
 *
 * - DB security_rule 이 없거나 비활성일 때만 폴백 규칙 사용.
 * - 필터 순서: SessionValidationFilter → JwtAuthenticationFilter.
 *   (세션 상태/touch → 인증 컨텍스트 주입)
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityRuleService ruleService;                  // DB 보안 규칙 조회
    private final CorsSettingService corsService;                   // CORS 설정 로더
    private final AppSettingService settingService;                 // 앱 설정(동적 규칙 on/off)
    private final ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilterProvider; // JWT 필터(옵셔널)
    private final SessionValidationFilter sessionValidationFilter;  // ✅ 세션 상태 필터

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF 비활성(Stateless + JWT 사용)
        http.csrf(csrf -> csrf.disable());
        // 세션 비사용(Stateless)
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        // CORS 동적 로딩
        http.cors(cors -> cors.configurationSource(request -> corsService.loadAsSpringConfig()));

        // 동적 보안 규칙 사용 여부
        boolean enabled = settingService.getFirstBooleanProfileAware(
                new String[]{"security.dynamic.enabled"}, true
        );
        List<SecurityRule> rules = enabled ? ruleService.loadActiveRules() : Collections.emptyList();
        boolean useFallback = !enabled || rules.isEmpty();

        if (useFallback) {
            log.warn("[SECURITY] DB 규칙 미사용/없음 → 폴백 정책 사용");
        } else {
            log.info("[SECURITY] DB 규칙 {}건 적용", rules.size());
        }

        http.authorizeHttpRequests(reg -> {
            if (useFallback) {
                // =========================
                // 폴백(고정) 규칙
                // =========================

                // CORS Preflight 허용
                reg.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                // 정적 리소스/기본 페이지/공개 업로드
                reg.requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll();
                reg.requestMatchers("/uploads/**").permitAll();

                // ✅ 인증 공개 API — 필요한 것만 명시적으로 허용
                reg.requestMatchers("/api/auth/login").permitAll();
                reg.requestMatchers("/api/auth/password-reset/**").permitAll();
                reg.requestMatchers("/api/auth/logout-legacy").permitAll();

                // 공개 조회(지역/학교)
                reg.requestMatchers("/api/common/regions", "/api/common/regions/**").permitAll();
                reg.requestMatchers(HttpMethod.GET, "/api/common/schools", "/api/common/schools/**").permitAll();

                // -------- 관리자 보호 영역 --------
                reg.requestMatchers(HttpMethod.GET,  "/api/admin/system/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_ADMIN");
                reg.requestMatchers(HttpMethod.POST, "/api/admin/system/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");

                reg.requestMatchers(HttpMethod.GET,    "/api/admin/db-connections/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_ADMIN");
                reg.requestMatchers(HttpMethod.POST,   "/api/admin/db-connections/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.PUT,    "/api/admin/db-connections/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.DELETE, "/api/admin/db-connections/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");

                reg.requestMatchers(HttpMethod.GET,    "/api/admin/auth/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_ADMIN");
                reg.requestMatchers(HttpMethod.PUT,    "/api/admin/auth/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.POST,   "/api/admin/auth/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.DELETE, "/api/admin/auth/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");

                reg.requestMatchers("/api/admin/schools/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN","ROLE_ADMIN");

                reg.requestMatchers(HttpMethod.GET, "/api/admin/regions/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN","ROLE_ADMIN");
                reg.requestMatchers(HttpMethod.POST,   "/api/admin/regions/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.PUT,    "/api/admin/regions/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.DELETE, "/api/admin/regions/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");

                // 그 외는 인증만 요구
                reg.anyRequest().authenticated();

            } else {
                // =========================
                // DB 동적 규칙 적용
                // =========================
                for (SecurityRule r : rules) {
                    String pattern = r.getPattern();
                    String method  = r.getHttpMethod();
                    SecurityRuleAccessType t = r.getAccessType();

                    boolean anyMethod = isAllOrBlank(method);
                    HttpMethod httpMethod = parseHttpMethodOrNull(method);

                    // "/path/**" → ["/path/**", "/path"] 로 확장하여 루트도 매칭
                    List<String> expanded = expandAntPattern(pattern);

                    for (String p : expanded) {
                        if (t == SecurityRuleAccessType.PERMIT_ALL) {
                            if (anyMethod) reg.requestMatchers(p).permitAll();
                            else reg.requestMatchers(httpMethod, p).permitAll();
                            continue;
                        }
                        if (t == SecurityRuleAccessType.DENY_ALL) {
                            if (anyMethod) reg.requestMatchers(p).denyAll();
                            else reg.requestMatchers(httpMethod, p).denyAll();
                            continue;
                        }
                        if (t == SecurityRuleAccessType.AUTHENTICATED) {
                            if (anyMethod) reg.requestMatchers(p).authenticated();
                            else reg.requestMatchers(httpMethod, p).authenticated();
                            continue;
                        }
                        if (t == SecurityRuleAccessType.HAS_ANY_AUTHORITY) {
                            String[] auths = splitCsv(r.getAuthoritiesCsv());
                            if (auths.length == 0) {
                                if (anyMethod) reg.requestMatchers(p).authenticated();
                                else reg.requestMatchers(httpMethod, p).authenticated();
                            } else {
                                if (anyMethod) reg.requestMatchers(p).hasAnyAuthority(auths);
                                else reg.requestMatchers(httpMethod, p).hasAnyAuthority(auths);
                            }
                        }
                    }
                }
                // 명시된 규칙 외에는 인증만 요구
                reg.anyRequest().authenticated();
            }
        });

        // 표준 예외 처리 핸들링
        http.exceptionHandling(Customizer.withDefaults());

        // ✅ 필터 순서 보장: 세션 → JWT
        http.addFilterBefore(sessionValidationFilter, UsernamePasswordAuthenticationFilter.class);

        JwtAuthenticationFilter authFilter = jwtAuthenticationFilterProvider.getIfAvailable();
        if (authFilter != null) {
            http.addFilterAfter(authFilter, SessionValidationFilter.class);
        }

        return http.build();
    }

    /** http_method가 비었거나 "ALL"이면 모든 메서드로 간주 */
    private static boolean isAllOrBlank(String method) {
        return method == null || method.trim().isEmpty() || "ALL".equalsIgnoreCase(method.trim());
    }

    /** 문자열 메서드를 HttpMethod 로 변환(잘못된 값이면 null) */
    private static HttpMethod parseHttpMethodOrNull(String method) {
        if (isAllOrBlank(method)) return null;
        try {
            return HttpMethod.valueOf(method.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    /** CSV 문자열을 권한 배열로 변환(공백 제거) */
    private String[] splitCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new String[0];
        return csv.replace(" ", "").split("\\s*,\\s*");
    }

    /**
     * Ant 패턴 확장:
     * - "/path/**" → ["/path/**", "/path"]
     * - "/**" 전역 패턴은 확장하지 않음
     * - 그 외 패턴은 그대로 1개만 반환
     */
    private static List<String> expandAntPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) return Collections.emptyList();
        String p = pattern.trim();
        if ("/**".equals(p)) return List.of(p); // 전역은 확장 불필요
        if (p.endsWith("/**")) {
            String root = p.substring(0, p.length() - 3); // "/**" 제거 → "/path"
            if (root.isEmpty()) root = "/";               // 안전장치
            return List.of(p, root);
        }
        return List.of(p);
    }
}