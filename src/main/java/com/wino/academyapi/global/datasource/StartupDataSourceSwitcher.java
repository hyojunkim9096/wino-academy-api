// src/main/java/com/wino/academyapi/global/datasource/StartupDataSourceSwitcher.java
package com.wino.academyapi.global.datasource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupDataSourceSwitcher implements ApplicationListener<ApplicationReadyEvent> {

    private final DataSourceReloadService dsReload;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            var r = dsReload.reloadFromActive(); // dev/prod 내 활성 중 id 가장 낮은 걸로
            log.info("[DS] Startup auto-switch applied: {}", r);
        } catch (Exception e) {
            // 활성 프로필이 하나도 없다면 여기로 옴 — 그냥 yml DS 유지
            log.warn("[DS] Startup auto-switch skipped: {}", e.toString());
        }
    }
}
