package com.wino.academyapi.global.file;

import com.wino.academyapi.domain.appsetting.service.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PublicUrlHelper {

    private final AppSettingService appSettingService;

    /**
     * DB에 저장된 경로를 /uploads/** 공개 URL로 변환
     * - 절대 경로면 basePath 프리픽스를 제거 후 상대 경로 계산
     * - 상대 경로면 그대로 /uploads/<상대경로>
     */
    public String toPublicUrl(String storedPath) {
        if (!StringUtils.hasText(storedPath)) return null;

        // basePath (profile-aware)
        String base = appSettingService.getProfileAware("storage.attach.base-path", "");

        // OS 구분자 정규화
        String norm = storedPath.replace('\\', '/');

        // basePrefix 제거
        String rel = norm;
        if (StringUtils.hasText(base)) {
            String baseNorm = base.replace('\\','/');
            if (baseNorm.endsWith("/")) baseNorm = baseNorm.substring(0, baseNorm.length()-1);
            if (norm.startsWith(baseNorm)) {
                rel = norm.substring(baseNorm.length());
                if (rel.startsWith("/")) rel = rel.substring(1);
            }
        }
        return "/uploads/" + rel;
    }
}
