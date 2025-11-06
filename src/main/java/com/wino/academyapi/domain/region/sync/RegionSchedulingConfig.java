// src/main/java/com/wino/academyapi/domain/region/sync/RegionSchedulingConfig.java
package com.wino.academyapi.domain.region.sync;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 인프라 활성화 전용 설정.
 *
 * - schedule-enabled=true 일 때만 @EnableScheduling 적용
 * - 실제 작업 메서드는 RegionSyncScheduler 에서 @Scheduled 로 정의
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.region.sync", name = "schedule-enabled", havingValue = "true")
public class RegionSchedulingConfig {
    // 빈 정의 없음 — @EnableScheduling 만 담당
}
