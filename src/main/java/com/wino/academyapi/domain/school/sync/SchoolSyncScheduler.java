// src/main/java/com/wino/academyapi/domain/school/sync/SchoolSyncScheduler.java
package com.wino.academyapi.domain.school.sync;

import com.wino.academyapi.domain.school.entity.SchoolStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 학교 동기화 스케줄러 (Provider 일반화 버전)
 *
 * 동작 개요
 * - cron 스케줄에 따라 SchoolSyncService.syncAll(...) 실행
 * - enrichAfter=true면 동기화 직후 지오코딩/법정동 보정(enrichGeoAndAdm) 실행
 * - 동시 실행 방지(AtomicBoolean)
 * - 하드스톱(app.school.sync.hard-stop-seconds)로 과도 실행 방지
 *
 * 설정 가이드 (application.yml)
 * ----------------------------------------------------------------
 * app:
 *   school:
 *     sync:
 *       schedule-enabled: true               # 스케줄러 켜기
 *       schedule-cron: "0 0 4 * * *"         # 매일 새벽 04:00
 *       schedule-zone: "Asia/Seoul"
 *       provider-enabled: false              # ✅ Provider 준비되면 true 로
 *       enrich-after: false                  # 동기화 직후 보정 실행 여부
 *       hard-stop-seconds: 3600              # 1시간 하드스톱
 *       partition-enabled: false             # 학부(E/M/H) 분할 실행할지
 *       scopes: ""                           # 콤마/공백 구분 범위 목록(없으면 전체)
 *       atpt-chunk-size: 0                   # 0=전체 1패스, n>0 이면 scopes 를 n개씩 순차
 *
 * 주의/운영 팁
 * - Provider(학교알리미 등)가 자체 분할/범위 옵션을 제공하면 여기 분할은 비활성 권장.
 * - 다중 인스턴스 환경에서는 분산락(ShedLock 등) 사용 권장.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.school.sync", name = "schedule-enabled", havingValue = "true")
@RequiredArgsConstructor
public class SchoolSyncScheduler {

    private final SchoolSyncService service;

    /** Provider 준비 여부(일반 스위치). 기본 false → 실수로 동작 방지 */
    @Value("${app.school.sync.provider-enabled:false}")
    private boolean providerEnabled;

    /** sync 후 enrich(지오코딩 보정)도 이어서 실행할지 여부 */
    @Value("${app.school.sync.enrich-after:false}")
    private boolean enrichAfter;

    /** 장기 실행 방지용 하드스톱(초). 기본 3600초=1시간 */
    @Value("${app.school.sync.hard-stop-seconds:3600}")
    private int hardStopSeconds;

    /** (선택) 스케줄러에서 학부(E/M/H) 분할 실행할지 — 기본 false */
    @Value("${app.school.sync.partition-enabled:false}")
    private boolean partitionEnabled;

    /** (선택) 범위(scopes) 목록(콤마/공백 구분). 비어있으면 서비스 기본/전체 */
    @Value("${app.school.sync.scopes:}")
    private String scopesCsv;

    /** (선택) scopes 를 청크로 잘라 순차 실행 (0 또는 음수면 한 번에 전체) */
    @Value("${app.school.sync.atpt-chunk-size:0}")
    private int chunkSize;

    /** 동시 실행 방지 플래그(단일 JVM 기준) */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 스케줄 엔트리
     * - cron/zone은 yml에서 제공
     * - schedule-enabled=false면 빈 생성 자체가 비활성이라 호출되지 않음
     */
    @Scheduled(cron = "${app.school.sync.schedule-cron}", zone = "${app.school.sync.schedule-zone:Asia/Seoul}")
    public void scheduledSync() {
        // 0) Provider 준비 안되면 스킵
        if (!providerEnabled) {
            log.info("[SchoolSync][SCHEDULE] provider-disabled → skip run");
            return;
        }
        // 1) 이미 실행 중이면 스킵(동시 실행 방지)
        if (!running.compareAndSet(false, true)) {
            log.warn("[SchoolSync][SCHEDULE] previous run still in progress → skip");
            return;
        }

        final long t0 = System.currentTimeMillis();
        long upserts = 0;
        long enriched = 0;

        try {
            final List<String> scopes = resolveScopes(scopesCsv);
            final int effChunk = (chunkSize <= 0) ? Math.max(1, scopes.size()) : chunkSize;

            log.info("[SchoolSync][SCHEDULE] start (providerEnabled={}, enrichAfter={}, hardStopSeconds={}, partitionEnabled={}, chunkSize={}, scopes={})",
                    providerEnabled, enrichAfter, hardStopSeconds, partitionEnabled,
                    (effChunk >= scopes.size() ? "<all>" : effChunk), (scopes.isEmpty() ? "<all>" : scopes));

            // 2) 동기화 실행
            if (!partitionEnabled) {
                // 2-1) 단일 패스(스테이지 미지정) — scopes가 비어있으면 null 전달(전체)
                if (scopes.isEmpty()) {
                    var r = service.syncAll(null, null, false);
                    upserts += r.upserts;
                    log.info("[SchoolSync][SCHEDULE] sync all → upserts={} skipped={} errors={}",
                            r.upserts, r.skipped, r.errors);
                } else {
                    for (int i = 0; i < scopes.size(); i += effChunk) {
                        List<String> sub = scopes.subList(i, Math.min(i + effChunk, scopes.size()));
                        var r = service.syncAll(new LinkedHashSet<>(sub), null, false);
                        upserts += r.upserts;
                        log.info("[SchoolSync][SCHEDULE] sync chunk={} upserts={} skipped={} errors={}",
                                sub, r.upserts, r.skipped, r.errors);
                    }
                }
            } else {
                // 2-2) 학부 분할(E/M/H) — Provider가 분할하지 않는 환경에서만 권장
                List<SchoolStage> stages = List.of(SchoolStage.E, SchoolStage.M, SchoolStage.H);
                if (scopes.isEmpty()) {
                    for (SchoolStage st : stages) {
                        var r = service.syncAll(null, Set.of(st), false);
                        upserts += r.upserts;
                        log.info("[SchoolSync][SCHEDULE] sync stage={} all → upserts={} skipped={} errors={}",
                                st, r.upserts, r.skipped, r.errors);
                    }
                } else {
                    for (SchoolStage st : stages) {
                        for (int i = 0; i < scopes.size(); i += effChunk) {
                            List<String> sub = scopes.subList(i, Math.min(i + effChunk, scopes.size()));
                            var r = service.syncAll(new LinkedHashSet<>(sub), Set.of(st), false);
                            upserts += r.upserts;
                            log.info("[SchoolSync][SCHEDULE] sync stage={} chunk={} upserts={} skipped={} errors={}",
                                    st, sub, r.upserts, r.skipped, r.errors);
                        }
                    }
                }
            }

            log.info("[SchoolSync][SCHEDULE] sync completed: upserts={} elapsed={}ms",
                    upserts, (System.currentTimeMillis() - t0));

            // 3) 하드스톱 검사 후 보정(enrich) 실행
            final long elapsedAfterSyncSec = (System.currentTimeMillis() - t0) / 1000;
            if (enrichAfter) {
                if (elapsedAfterSyncSec >= hardStopSeconds) {
                    log.warn("[SchoolSync][SCHEDULE] hard stop reached before enrich ({}s ≥ {}s) → skip enrich",
                            elapsedAfterSyncSec, hardStopSeconds);
                } else {
                    enriched = service.enrichGeoAndAdm();
                    log.info("[SchoolSync][SCHEDULE] enrich done: enriched={}", enriched);
                }
            } else {
                log.info("[SchoolSync][SCHEDULE] enrichAfter=false → enrich step skipped");
            }
        } catch (Exception e) {
            // 전체 스케줄러 실패로 이어지지 않도록 잡고 로그만 남김
            log.warn("[SchoolSync][SCHEDULE] FAILED: {}", e.getMessage(), e);
        } finally {
            final long tookMs = System.currentTimeMillis() - t0;
            // 메모리 사용량 로그(관찰성 보강)
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            log.info("[SchoolSync][SCHEDULE] finish: upserts={} enriched={} took={}ms usedMem={}MB",
                    upserts, enriched, tookMs, usedMb);
            running.set(false);
        }
    }

    /* ========================= 내부 유틸 ========================= */

    /** scopes CSV → 리스트(공백/콤마 구분, 대문자 정규화) */
    private static List<String> resolveScopes(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        String[] arr = csv.split("[,\\s]+");
        List<String> list = new ArrayList<>();
        for (String a : arr) {
            if (a != null && !a.isBlank()) list.add(a.trim().toUpperCase(Locale.ROOT));
        }
        return list;
    }
}
