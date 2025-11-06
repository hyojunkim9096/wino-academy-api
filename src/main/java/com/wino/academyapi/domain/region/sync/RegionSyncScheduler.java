// src/main/java/com/wino/academyapi/domain/region/sync/RegionSyncScheduler.java
package com.wino.academyapi.domain.region.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 지역 동기화 스케줄 작업
 *
 * 동작 조건:
 * - app.region.sync.enabled=true && app.region.sync.schedule-enabled=true 일 때만 빈 등록
 *   (둘 중 하나라도 false면 스케줄러 자체가 생성되지 않음)
 * - @EnableScheduling 은 별도 @Configuration 클래스(RegionSchedulingConfig)에서 활성화
 *
 * 동시 실행 방지:
 * - AtomicBoolean 으로 이전 실행이 끝나지 않았으면 즉시 skip
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.region.sync",
        name = { "enabled", "schedule-enabled" },   // ✅ 두 조건 모두 true 여야 함
        havingValue = "true"
)
@RequiredArgsConstructor
public class RegionSyncScheduler {

    private final RegionSyncService service;
    private final RegionSyncProperties props;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 스케줄 크론/타임존
     * - yml에 값이 없어도 애플리케이션이 구동되도록 기본값을 플레이스홀더에 명시
     *   · cron 기본: 매일 03:30
     *   · zone 기본: Asia/Seoul
     */
    @Scheduled(
            cron = "${app.region.sync.schedule-cron:0 30 3 * * *}",               // ✅ 기본값 추가
            zone = "${app.region.sync.schedule-zone:Asia/Seoul}"                  // ✅ 기본값 유지
    )
    public void scheduledSync() {
        // ✅ 실행 가드: enabled=false 라면(설정이 중간에 바뀌었을 수도 있음) 바로 skip
        if (!props.isEnabled()) {
            log.info("[RegionSync][SCHEDULE] disabled by property → skip");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.warn("[RegionSync][SCHEDULE] previous run still in progress → skip");
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            var mode = "replace".equalsIgnoreCase(props.getDefaultMode())
                    ? RegionSyncService.Mode.REPLACE
                    : RegionSyncService.Mode.UPSERT;

            var r = service.sync(mode, null);
            long ms = System.currentTimeMillis() - t0;

            log.info("[RegionSync][SCHEDULE] mode={} total={} inserted={} updated={} disabled={} took={}ms",
                    mode, r.total(), r.inserted(), r.updated(), r.disabled(), ms);

        } catch (Exception e) {
            // 외부 API/네트워크 문제 포함 — 실패만 기록하고 다음 스케줄에서 재시도
            log.warn("[RegionSync][SCHEDULE] FAILED: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}
