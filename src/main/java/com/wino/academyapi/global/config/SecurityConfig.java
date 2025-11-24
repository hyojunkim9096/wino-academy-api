// src/main/java/com/wino/academyapi/global/config/SecurityConfig.java
package com.wino.academyapi.global.config;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import com.wino.academyapi.domain.cors.service.CorsSettingService;
import com.wino.academyapi.domain.securityrule.entity.SecurityRule;
import com.wino.academyapi.domain.securityrule.entity.SecurityRuleAccessType;
import com.wino.academyapi.domain.securityrule.service.SecurityRuleService;
import com.wino.academyapi.global.jwt.JwtAuthenticationFilter;
import com.wino.academyapi.global.security.SessionValidationFilter;
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
    private final ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilterProvider;
    private final SessionValidationFilter sessionValidationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 1. 기본 보안 설정 (세션 안 씀, CSRF 끔)
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.cors(cors -> cors.configurationSource(request -> corsService.loadAsSpringConfig()));

        // 2. DB에서 동적 보안 규칙 가져오기 (security_rule 테이블)
        boolean enabled = settingService.getFirstBooleanProfileAware(
                new String[]{"security.dynamic.enabled"}, true
        );
        // 활성화되어 있으면 DB 조회, 아니면 빈 리스트
        List<SecurityRule> rules = enabled ? ruleService.loadActiveRules() : Collections.emptyList();

        // DB에 규칙이 하나도 없거나 기능이 꺼져있으면 -> 폴백(기본 하드코딩) 모드 사용
        boolean useFallback = !enabled || rules.isEmpty();

        if (useFallback) {
            log.warn("[SECURITY] DB 규칙이 없거나 미사용 상태입니다 → 기본(폴백) 정책을 사용합니다.");
        } else {
            log.info("[SECURITY] DB 동적 규칙 {}건을 적용합니다. (단, 기본 공개 경로는 항상 우선 적용됨)", rules.size());
        }

        // 3. 경로별 권한 설정 (여기가 제일 중요!)
        http.authorizeHttpRequests(reg -> {
            // ================================================================
            // ✅ [핵심 수정] DB 설정과 무관하게 "무조건 허용"할 경로를 맨 위에 배치
            //    이렇게 하면 DB에 규칙이 있든 없든, 가입/로그인은 403 없이 통과됩니다.
            // ================================================================

            // (1) Preflight 요청 (CORS를 위해 필수)
            reg.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

            // (2) 정적 파일 (이미지, 파비콘 등)
            reg.requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico").permitAll();
            reg.requestMatchers("/uploads/**").permitAll();

            // (3) 인증/가입 관련 API (여기가 막혀서 403이 떴던 것!)
            reg.requestMatchers(
                    "/api/auth/login",             // 로그인
                    "/api/auth/password-reset/**", // 비번 찾기
                    "/api/auth/logout-legacy",     // 구버전 로그아웃
                    "/api/admin/register",         // ✅ 관리자 신규 가입 (이게 핵심!)
                    "/api/admin/signUp"            // ✅ (호환용)
            ).permitAll();

            // (4) 공통 데이터 조회 (지역, 학교 검색 등 로그인 전에도 필요할 수 있음)
            reg.requestMatchers("/api/common/regions", "/api/common/regions/**").permitAll();
            reg.requestMatchers(HttpMethod.GET, "/api/common/schools", "/api/common/schools/**").permitAll();

            // ================================================================
            // 4. 그 외 경로: DB 규칙(동적) vs 기본 규칙(폴백) 분기 처리
            // ================================================================
            if (useFallback) {
                // [폴백 모드] DB에 규칙이 없을 때 적용되는 기본 하드코딩 규칙

                // 시스템, DB 설정 등 민감한 기능은 최고 관리자만
                reg.requestMatchers(HttpMethod.GET,  "/api/admin/system/**", "/api/admin/db-connections/**", "/api/admin/auth/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_ADMIN");

                // 데이터 변경(POST/PUT/DELETE)은 더 엄격하게 SYSTEM_ADMIN 만
                reg.requestMatchers(HttpMethod.POST, "/api/admin/system/**", "/api/admin/db-connections/**", "/api/admin/auth/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.PUT,  "/api/admin/db-connections/**", "/api/admin/auth/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");
                reg.requestMatchers(HttpMethod.DELETE, "/api/admin/db-connections/**", "/api/admin/auth/**")
                        .hasAuthority("ROLE_SYSTEM_ADMIN");

                // 학교/지역 관리 등
                reg.requestMatchers("/api/admin/schools/**", "/api/admin/regions/**")
                        .hasAnyAuthority("ROLE_SYSTEM_ADMIN", "ROLE_ADMIN");

            } else {
                // [동적 모드] DB(security_rule 테이블)에 있는 규칙을 하나씩 적용
                for (SecurityRule r : rules) {
                    String pattern = r.getPattern();
                    String method  = r.getHttpMethod();
                    SecurityRuleAccessType t = r.getAccessType();

                    boolean anyMethod = isAllOrBlank(method); // 메서드가 없거나 ALL이면 모든 HTTP 메서드
                    HttpMethod httpMethod = parseHttpMethodOrNull(method);
                    List<String> expanded = expandAntPattern(pattern); // /** 처리

                    for (String p : expanded) {
                        if (t == SecurityRuleAccessType.PERMIT_ALL) {
                            if (anyMethod) reg.requestMatchers(p).permitAll();
                            else reg.requestMatchers(httpMethod, p).permitAll();
                        } else if (t == SecurityRuleAccessType.DENY_ALL) {
                            if (anyMethod) reg.requestMatchers(p).denyAll();
                            else reg.requestMatchers(httpMethod, p).denyAll();
                        } else if (t == SecurityRuleAccessType.AUTHENTICATED) {
                            if (anyMethod) reg.requestMatchers(p).authenticated();
                            else reg.requestMatchers(httpMethod, p).authenticated();
                        } else if (t == SecurityRuleAccessType.HAS_ANY_AUTHORITY) {
                            // 권한 목록 파싱 (쉼표로 구분된 문자열)
                            String[] auths = splitCsv(r.getAuthoritiesCsv());
                            if (auths.length > 0) {
                                if (anyMethod) reg.requestMatchers(p).hasAnyAuthority(auths);
                                else reg.requestMatchers(httpMethod, p).hasAnyAuthority(auths);
                            } else {
                                // 권한이 설정되어야 하는데 없으면 일단 인증된 사람만 허용
                                if (anyMethod) reg.requestMatchers(p).authenticated();
                                else reg.requestMatchers(httpMethod, p).authenticated();
                            }
                        }
                    }
                }
            }

            // 5. 위에서 언급되지 않은 나머지 모든 요청은 인증된 사람만 접근 가능
            reg.anyRequest().authenticated();
        });

        // 에러 핸들링 기본 설정
        http.exceptionHandling(Customizer.withDefaults());

        // 필터 순서 설정: 세션 검증 -> JWT 인증 -> 스프링 시큐리티 처리
        http.addFilterBefore(sessionValidationFilter, UsernamePasswordAuthenticationFilter.class);

        JwtAuthenticationFilter authFilter = jwtAuthenticationFilterProvider.getIfAvailable();
        if (authFilter != null) {
            http.addFilterAfter(authFilter, SessionValidationFilter.class);
        }

        return http.build();
    }

    // --- 내부 유틸 함수들 ---

    // 메서드가 비어있거나 "ALL"이면 true
    private static boolean isAllOrBlank(String method) {
        return method == null || method.trim().isEmpty() || "ALL".equalsIgnoreCase(method.trim());
    }

    // 문자열을 HttpMethod 객체로 변환 (실패시 null)
    private static HttpMethod parseHttpMethodOrNull(String method) {
        if (isAllOrBlank(method)) return null;
        try {
            return HttpMethod.valueOf(method.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    // 콤마로 구분된 권한 문자열 분리
    private String[] splitCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) return new String[0];
        return csv.replace(" ", "").split("\\s*,\\s*");
    }

    // Ant 패턴 확장 (/** 가 있으면 상위 경로도 포함해서 처리)
    private static List<String> expandAntPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) return Collections.emptyList();
        String p = pattern.trim();
        if ("/**".equals(p)) return List.of(p);
        if (p.endsWith("/**")) {
            String root = p.substring(0, p.length() - 3);
            if (root.isEmpty()) root = "/";
            return List.of(p, root);
        }
        return List.of(p);
    }
}