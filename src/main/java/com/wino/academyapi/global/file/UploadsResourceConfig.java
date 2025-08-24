package com.wino.academyapi.global.file;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 파일 시스템 폴더를 /uploads/** 로 서빙
 * - basePath: storage.attach.base-path (DB AppSetting, 프로필 인지)
 *   · 예) dev: D:\projects\attach_file
 *   · 예) prod: /data/wino-uploads
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UploadsResourceConfig implements WebMvcConfigurer {

    private final AppSettingService appSettingService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 1) DB(AppSetting)에서 profile-aware 로 가져오기
        String base = appSettingService.getProfileAware("storage.attach.base-path", "/data/wino-uploads");

        // 2) 비었으면 홈 밑 폴lder 사용(폴백)
        if (!StringUtils.hasText(base)) {
            base = System.getProperty("user.home") + "/wino-uploads";
        }

        // 3) file: 스킴으로 안전 변환 (끝 슬래시 보장)
        Path p = Paths.get(base).toAbsolutePath();
        String location = p.toUri().toString(); // file:/D:/projects/attach_file/ 또는 file:/data/wino-uploads/
        if (!location.endsWith("/")) location += "/";

        log.info("[Uploads] Map /uploads/** -> {}", location);
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(60 * 60 * 24 * 7)   // 7일 캐시
                .resourceChain(true);
    }
}
