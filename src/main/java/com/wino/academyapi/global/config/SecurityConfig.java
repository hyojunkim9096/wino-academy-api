// src/main/java/com/wino/academyapi/global/config/SecurityConfig.java
package com.wino.academyapi.global.config;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import com.wino.academyapi.domain.cors.service.CorsSettingService;
import com.wino.academyapi.domain.securityrule.entity.SecurityRule;
import com.wino.academyapi.domain.securityrule.entity.SecurityRuleAccessType;
import com.wino.academyapi.domain.securityrule.service.SecurityRuleService;
import com.wino.academyapi.global.jwt.JwtAuthenticationFilter;

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

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityRuleService ruleService;
    private final CorsSettingService corsService;
    private final AppSettingService settingService;

    // JwtAuthenticationFilter를 선택 주입(없어도 부팅됨)
    private final ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilterProvider;

    /** ✅ PasswordEncoder 빈 (Delegating - 기본 bcrypt) */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // ── 공통 설정 ───────────────────────────────────────────────────────────────
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        // 인라인 CORS 구성(Bean 이름 충돌 방지)
        http.cors(cors -> cors.configurationSource(request -> corsService.loadAsSpringConfig()));

        // ── 동적 규칙 사용 여부 판단(profile-aware) ────────────────────────────────
        boolean enabled = settingService.getFirstBooleanProfileAware(
                new String[]{"security.dynamic.enabled"}, true
        );
        List<SecurityRule> rules = enabled ? ruleService.loadActiveRules() : Collections.emptyList();
        boolean useFallback = !enabled || rules.isEmpty();

        if (useFallback) log.warn("[SECURITY] DB 규칙 미사용/없음 → 폴백 정책 사용");
        else log.info("[SECURITY] DB 규칙 {}건 적용", rules.size());

        // ── 인가 규칙 ──────────────────────────────────────────────────────────────
        http.authorizeHttpRequests(reg -> {
            if (useFallback) {
                // ───────────────────── 폴백 규칙 ─────────────────────
                // CORS 사전요청 허용
                reg.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                // 공개 리소스
                reg.requestMatchers("/uploads/**").permitAll();
                reg.requestMatchers("/api/auth/**").permitAll();
                reg.requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll();

                // 시스템 관리 API
                reg.requestMatchers(HttpMethod.GET,  "/api/admin/system/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_ADMIN");
                reg.requestMatchers(HttpMethod.POST, "/api/admin/system/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");

                // ✅ DB 연결 프로필 API (추가)
                reg.requestMatchers(HttpMethod.GET,    "/api/admin/db-connections/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_ADMIN");
                reg.requestMatchers(HttpMethod.POST,   "/api/admin/db-connections/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.PUT,    "/api/admin/db-connections/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.DELETE, "/api/admin/db-connections/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");

                // 관리자 인증 API (기존 정책 유지)
                reg.requestMatchers(HttpMethod.GET,    "/api/admin/auth/**").hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_ADMIN");
                reg.requestMatchers(HttpMethod.PUT,    "/api/admin/auth/**").hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.POST,   "/api/admin/auth/**").hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.DELETE, "/api/admin/auth/**").hasAuthority("ROLE_SYSTEM_ADMIN");

                // 그 외는 보호
                reg.anyRequest().authenticated();

            } else {
                // ───────────────────── DB 동적 규칙 ───────────────────
                for (SecurityRule r : rules) {
                    String pattern = r.getPattern();
                    String method  = r.getHttpMethod();
                    SecurityRuleAccessType t = r.getAccessType();

                    boolean anyMethod = isAllOrBlank(method);
                    HttpMethod httpMethod = parseHttpMethodOrNull(method);

                    if (t == SecurityRuleAccessType.PERMIT_ALL) {
                        if (anyMethod) reg.requestMatchers(pattern).permitAll();
                        else reg.requestMatchers(httpMethod, pattern).permitAll();
                        continue;
                    }
                    if (t == SecurityRuleAccessType.DENY_ALL) {
                        if (anyMethod) reg.requestMatchers(pattern).denyAll();
                        else reg.requestMatchers(httpMethod, pattern).denyAll();
                        continue;
                    }
                    if (t == SecurityRuleAccessType.AUTHENTICATED) {
                        if (anyMethod) reg.requestMatchers(pattern).authenticated();
                        else reg.requestMatchers(httpMethod, pattern).authenticated();
                        continue;
                    }
                    if (t == SecurityRuleAccessType.HAS_ANY_AUTHORITY) {
                        String[] auths = splitCsv(r.getAuthoritiesCsv());
                        if (auths.length == 0) {
                            if (anyMethod) reg.requestMatchers(pattern).authenticated();
                            else reg.requestMatchers(httpMethod, pattern).authenticated();
                        } else {
                            if (anyMethod) reg.requestMatchers(pattern).hasAnyAuthority(auths);
                            else reg.requestMatchers(httpMethod, pattern).hasAnyAuthority(auths);
                        }
                    }
                }
                // 기본 보호
                reg.anyRequest().authenticated();
            }
        });

        // 예외 처리(기본)
        http.exceptionHandling(Customizer.withDefaults());

        // JWT 필터 연결(존재할 때만)
        JwtAuthenticationFilter authFilter = jwtAuthenticationFilterProvider.getIfAvailable();
        if (authFilter != null) {
            http.addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────────
    private static boolean isAllOrBlank(String method) {
        return method == null || method.trim().isEmpty() || "ALL".equalsIgnoreCase(method.trim());
    }

    private static HttpMethod parseHttpMethodOrNull(String method) {
        if (isAllOrBlank(method)) return null;
        try {
            return HttpMethod.valueOf(method.trim().toUpperCase());
        } catch (Exception e) {
            return null; // 잘못된 값은 any로 처리
        }
    }

    private String[] splitCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new String[0];
        return csv.replace(" ", "").split("\\s*,\\s*");
    }
}
