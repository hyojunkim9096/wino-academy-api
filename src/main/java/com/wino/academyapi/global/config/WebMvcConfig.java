package com.wino.academyapi.global.config;

import com.wino.academyapi.global.audit.AppUserSqlVarInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** WebMvc 인터셉터 등록 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppUserSqlVarInterceptor appUserSqlVarInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 모든 API 요청에 적용 (필요 시 제외 경로 추가)
        registry.addInterceptor(appUserSqlVarInterceptor)
                .addPathPatterns("/api/**");
        // .excludePathPatterns("/api/health", "/api/auth/**");
    }
}