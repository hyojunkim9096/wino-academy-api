// src/main/java/com/wino/academyapi/global/audit/WebFilterConfig.java
package com.wino.academyapi.global.audit;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;

/**
 * 서블릿 필터 등록
 *
 * - AdminActionLoggingFilter는 내부에서 ObjectProvider<AdminSystemLogService>로
 *   서비스를 "지연 획득"하므로, 여기서는 필터 빈만 주입해 안전하게 등록한다.
 * - 보안 필터 체인(Spring Security) 뒤쪽에서 동작하도록 order를 높게 설정.
 * - URL 패턴은 넓게("/*") 등록하고, 실제 /api/admin/** 여부는
 *   AdminActionLoggingFilter.shouldNotFilter(...)에서 선별한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebFilterConfig {

    private final AdminActionLoggingFilter adminActionLoggingFilter;

    @Bean
    public FilterRegistrationBean<AdminActionLoggingFilter> adminAuditFilterRegistration() {
        FilterRegistrationBean<AdminActionLoggingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(adminActionLoggingFilter);

        // ✅ 넓게 등록한 뒤, 필터 내부 shouldNotFilter()로 /api/admin/**만 선별
        //    (직접 "/api/admin/*"로 제한해도 되지만, 경로 가변성/서블릿 매핑 이슈를 피하려면 이 방식이 안전)
        reg.addUrlPatterns("/*");

        // ✅ 보안 필터 체인(대개 음수 order) 이후에 실행되도록 충분히 큰 값
        //    Spring Security의 FilterChainProxy는 일반적으로 낮은 order로 등록됨.
        reg.setOrder(100);

        // (선택) 톰캣 비동기 서블릿 환경 지원
        reg.setAsyncSupported(true);

        // (선택) 가독성을 위한 이름 부여
        reg.setName("adminActionLoggingFilter");

        return reg;
        // 참고:
        // - setMatchAfter(true)는 DispatcherType 매칭 순서에 영향; 보통 order로 충분.
    }
}
