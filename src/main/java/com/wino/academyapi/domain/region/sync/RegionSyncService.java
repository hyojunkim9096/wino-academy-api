// src/main/java/com/wino/academyapi/domain/region/sync/RegionSyncService.java
package com.wino.academyapi.domain.region.sync;

import com.wino.academyapi.domain.region.entity.Region;
import com.wino.academyapi.domain.region.repository.RegionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/**
 * 지역 데이터 동기화 서비스
 *
 * 모드:
 *  1) REPLACE : 테이블 초기화 후 전체 재삽입
 *  2) UPSERT  : 존재하면 업데이트, 없으면 삽입 (+ 옵션: 소스에 없는 코드는 use_yn=false)
 *
 * 설계 포인트(중요):
 *  - 외부 API 호출(fetch)은 반드시 트랜잭션 "밖"에서 수행
 *    → 긴 네트워크 대기 동안 DB 커넥션/세션 점유 방지, 롤백 영향 축소
 *  - DB 반영(replace/upsert)만 트랜잭션으로 묶는다.
 *
 * 점진/배치:
 *  - syncMissingTopN(limit) : 최신 소스 1회 fetch → 상위 N건 후보만 UPSERT
 *      · ⚠ self-invocation으로 @Transactional이 먹지 않는 문제를 피하기 위해 TransactionTemplate로 트랜잭션 경계 지정
 *  - syncMissingAll(chunk, prefixes, hardStopSeconds)
 *      : 최신 소스 1회 fetch → 전체 후보 diff를 chunk로 분할,
 *        각 chunk는 REQUIRES_NEW 트랜잭션으로 안전하게 UPSERT(메모리/쿼터 안정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionSyncService {

    private final RegionRepository repo;
    private final RegionSourceProvider provider;
    private final RegionSyncProperties props;

    /** 배치 간 간단 쿨다운 (DB/외부 API 과열 방지) */
    private static final long BATCH_SLEEP_MS = 300L;

    /** 시/도 2자리 기본 prefix(표시/로그용) */
    private static final List<String> DEFAULT_SIDO_PREFIXES = List.of(
            "11","26","27","28","29","30","31","36","41","42","43","44","45","46","47","48","50"
    );

    public enum Mode { REPLACE, UPSERT }

    // ───────────────── 편의 메서드 ─────────────────

    /** 기본(UPSERT) 전체 동기화. (inserted+updated) 개수 반환 */
    public int syncAll() throws Exception {
        Result r = sync(Mode.UPSERT, null);
        return r.inserted + r.updated;
    }

    /** 기본(UPSERT) 전체 동기화. 상세 결과 반환 */
    public Result syncUpsert() throws Exception {
        return sync(Mode.UPSERT, null);
    }

    /** REPLACE 전체 동기화. 상세 결과 반환 */
    public Result syncReplace() throws Exception {
        return sync(Mode.REPLACE, null);
    }

    /**
     * 공용 동기화: 외부 fetch는 트랜잭션 밖에서 1회, DB 반영은 트랜잭션 안에서
     *
     * ⚠️ 중요 수정:
     *  - self-invocation(@Transactional 메서드를 같은 클래스에서 호출) 문제로
     *    트랜잭션이 적용되지 않을 수 있으므로, 여기서 TransactionTemplate으로
     *    DB 반영 구간을 명시적으로 트랜잭션 처리한다.
     */
    public Result sync(Mode mode, String sourceUrlOverride) throws Exception {
        if (!props.isEnabled()) {
            log.warn("[RegionSync] disabled by config");
            return new Result(0, 0, 0, 0);
        }

        // 1) 트랜잭션 밖에서 외부 호출 (반드시 1회)
        List<RegionSourceProvider.Row> rows = provider.fetch(sourceUrlOverride);
        if (rows.isEmpty()) {
            log.info("[RegionSync] fetched=0 → nothing to do");
            return new Result(0, 0, 0, 0);
        }

        // 2) DB 반영은 명시 트랜잭션으로 묶어 처리 (self-invocation 방지)
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        tx.setReadOnly(false);

        return tx.execute(status -> {
            switch (mode) {
                case REPLACE:
                    return replaceAll(rows);                              // ← 내부 @Transactional 유무와 무관하게 템플릿 트랜잭션에 합류
                case UPSERT:
                    return upsert(rows, props.isAutoDisableMissing());    // ← 전체 upsert 시에만 disableMissing 적용
                default:
                    return new Result(0,0,0,0);
            }
        });
    }

    // =====================================================================
    // ✅ 점진/배치 동기화
    // =====================================================================

    /**
     * "변경/누락 후보" 상위 N건만 추려 UPSERT
     * - 외부 fetch는 1회
     * - DB 반영(upsert)은 TransactionTemplate으로 한 덩어리 트랜잭션을 보장
     *
     * ⚠ why TransactionTemplate?
     *   - 같은 클래스 내부에서 @Transactional 메서드를 호출하면 프록시를 거치지 않는 self-invocation이 되어
     *     트랜잭션이 열리지 않을 수 있음 → 명시적으로 템플릿으로 경계 설정
     */
    public int syncMissingTopN(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));

        // 1) 최신 소스 전체 1회 fetch (트랜잭션 밖)
        final List<RegionSourceProvider.Row> rows;
        try {
            rows = provider.fetch(null);
        } catch (Exception e) {
            throw new IllegalStateException("외부 소스 조회 실패: " + e.getMessage(), e);
        }
        if (rows.isEmpty()) return 0;

        // 2) 기존 DB 맵 (스냅샷)
        Map<String, Region> existing = loadExistingMap();

        // 3) 변경/신규 후보만 limit개 선별
        List<RegionSourceProvider.Row> candidates = new ArrayList<>(safeLimit);
        for (RegionSourceProvider.Row row : rows) {
            if (candidates.size() >= safeLimit) break;
            Region cur = existing.get(row.code());
            if (cur == null || isChanged(cur, row)) {
                candidates.add(row);
            }
        }
        if (candidates.isEmpty()) return 0;

        // 4) 부분 UPSERT (missing 비활성화 X) — ★ 트랜잭션으로 묶어서 처리 + flush/clear
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Integer processed = tx.execute(status -> {
            Result r = upsertWithExisting(candidates, false, existing);
            em.flush();
            em.clear();
            return r.inserted + r.updated;
        });
        return processed == null ? 0 : processed;
    }

    /**
     * ✅ 누락/변경 전량을 chunk 단위로 반복 UPSERT
     * - 외부 fetch는 여기서 "1회만" 수행하고, 로컬에서 diff 전체를 chunk 분할
     * - 각 배치는 REQUIRES_NEW 트랜잭션으로 분리
     * - prefixes는 Provider가 prefix fetch를 지원하지 않아도 표시/로그용으로만 기록
     * - hardStopSeconds에 도달하면 안전 종료
     *
     * 성능 최적화:
     * - chunk 반복 시 매번 DB 전체를 재조회하지 않도록 existing 맵을 재사용
     *   → upsertWithExisting(...)가 동일 맵을 갱신해가며 처리
     */
    public SyncAllReport syncMissingAll(int chunkSize, List<String> prefixes, int hardStopSeconds) {
        final int safeChunk = Math.max(1, Math.min(chunkSize, 500));
        final int safeHard  = Math.max(60, hardStopSeconds);

        // 0) 외부에서 "한 번만" 전체 rows를 받아옴 (트랜잭션 밖)
        final List<RegionSourceProvider.Row> allRows;
        try {
            allRows = provider.fetch(null);
        } catch (Exception e) {
            throw new IllegalStateException("외부 소스 조회 실패: " + e.getMessage(), e);
        }
        if (allRows.isEmpty()) {
            return new SyncAllReport(); // 아무 것도 처리할 게 없음
        }

        // 1) 현재 DB와 비교해 "신규/변경" 전체 리스트 도출
        Map<String, Region> existing = loadExistingMap();
        List<RegionSourceProvider.Row> candidates = new ArrayList<>(allRows.size());
        for (RegionSourceProvider.Row row : allRows) {
            Region cur = existing.get(row.code());
            if (cur == null || isChanged(cur, row)) {
                candidates.add(row);
            }
        }
        if (candidates.isEmpty()) {
            return new SyncAllReport(); // 이미 최신
        }

        // 2) 배치 템플릿 (REQUIRES_NEW)
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setReadOnly(false);

        // 3) 보고서/파라미터 준비
        SyncAllReport report = new SyncAllReport();
        report.prefixesUsed.addAll(
                (prefixes == null || prefixes.isEmpty()) ? DEFAULT_SIDO_PREFIXES : prefixes
        );
        long start = System.currentTimeMillis();

        // 4) candidates 를 chunk 로 나눠 반복 처리
        int idx = 0;
        while (idx < candidates.size()) {
            long elapsedSec = (System.currentTimeMillis() - start) / 1000;
            if (elapsedSec >= safeHard) {
                log.warn("[RegionSyncAll] hard stop reached ({}s). stopping...", safeHard);
                break;
            }

            int end = Math.min(idx + safeChunk, candidates.size());
            final List<RegionSourceProvider.Row> slice = candidates.subList(idx, end);

            Integer processed = tx.execute(status -> {
                // 기존 맵을 재사용하여 불필요한 DB 전체재조회 방지
                Result r = upsertWithExisting(slice, false, existing);
                em.flush();
                em.clear();
                return r.inserted + r.updated;
            });

            int done = processed == null ? 0 : processed;
            report.addBatch(done);
            idx = end;

            try { Thread.sleep(BATCH_SLEEP_MS); } catch (InterruptedException ignored) {}
        }

        report.finish(System.currentTimeMillis() - start);
        log.info("[RegionSyncAll] totalProcessed={} batches={} elapsedMs={}",
                report.getProcessedTotal(), report.getBatches(), report.getElapsedMs());

        return report;
    }

    // =====================================================================
    // DB 반영 구현부 (트랜잭션 범위)
    // =====================================================================

    @PersistenceContext
    private EntityManager em;

    /** 배치 분리 트랜잭션용 */
    private final PlatformTransactionManager txManager;

    /**
     * REPLACE: 테이블 초기화 후 일괄 삽입
     * - truncate 실패 시 deleteAllInBatch로 폴백
     *
     * (참고) sync()에서 TransactionTemplate으로 트랜잭션을 열고 호출하므로
     *        여기 @Transactional 유무와 상관없이 동일 트랜잭션으로 합류한다.
     */
    @Transactional
    protected Result replaceAll(List<RegionSourceProvider.Row> rows) {
        try {
            repo.truncate(); // DDL 권한/엔진 이슈 등으로 실패 가능
        } catch (Exception e) {
            log.warn("[RegionSync] TRUNCATE failed. Fallback to deleteAllInBatch. msg={}", e.getMessage());
            repo.deleteAllInBatch();
        }

        int inserted = 0;
        final int CHUNK = Math.max(100, props.getBatchSize());
        List<Region> buf = new ArrayList<>(CHUNK);

        for (var r : rows) {
            buf.add(toEntity(r));
            if (buf.size() == CHUNK) {
                repo.saveAll(buf);
                inserted += buf.size();
                buf.clear();
            }
        }
        if (!buf.isEmpty()) {
            repo.saveAll(buf);
            inserted += buf.size();
        }

        em.flush();
        em.clear();

        return new Result(rows.size(), inserted, 0, 0);
    }

    /**
     * UPSERT: 존재하면 갱신, 없으면 삽입
     * - disableMissing: 소스에 없는 코드는 use_yn=false 처리
     * - 내부에서 existing 맵을 새로 로드하는 표준 버전
     *
     * (참고) sync()에서 TransactionTemplate으로 트랜잭션을 열고 호출하므로
     *        여기 @Transactional 유무와 상관없이 동일 트랜잭션으로 합류한다.
     */
    @Transactional
    protected Result upsert(List<RegionSourceProvider.Row> rows, boolean disableMissing) {
        Map<String, Region> existing = loadExistingMap();
        return upsertWithExisting(rows, disableMissing, existing);
    }

    /**
     * ✅ 성능 최적화 버전: 외부에서 전달된 existing 맵을 재사용하며 UPSERT
     * - chunk 반복 시 매번 DB 전체 재조회 방지
     * - 이 메서드 내부에서 existing 맵을 최신 상태로 갱신해 다음 chunk가 재사용 가능
     *
     * (참고) 상위에서 TransactionTemplate을 사용해 트랜잭션을 열고 들어온다.
     */
    @Transactional
    protected Result upsertWithExisting(List<RegionSourceProvider.Row> rows,
                                        boolean disableMissing,
                                        Map<String, Region> existing) {

        int inserted = 0, updated = 0;
        Set<String> newCodes = new HashSet<>();

        for (var row : rows) {
            newCodes.add(row.code());
            Region cur = existing.get(row.code());
            if (cur == null) {
                Region created = repo.save(toEntity(row));
                existing.put(row.code(), created); // 맵에 즉시 반영
                inserted++;
            } else if (applyIfChanged(cur, row)) {
                Region saved = repo.save(cur);
                existing.put(row.code(), saved);   // 변경 반영
                updated++;
            }
        }

        int disabled = 0;
        if (disableMissing && !newCodes.isEmpty()) {
            // 전체 upsert 모드에서만 사용 — 부분 배치/점진 개선 경로에서는 호출하지 않음
            disabled = repo.bulkDisableMissing(newCodes, true);
        }

        return new Result(rows.size(), inserted, updated, disabled);
    }

    // =====================================================================
    // 매핑/비교 유틸
    // =====================================================================

    /** 현재 테이블 전체를 맵(PK=code)으로 적재 */
    private Map<String, Region> loadExistingMap() {
        Map<String, Region> existing = new HashMap<>();
        for (Region r : repo.findAll()) existing.put(r.getCode(), r);
        return existing;
    }

    /** Provider.Row → Entity 변환 */
    private Region toEntity(RegionSourceProvider.Row r) {
        return Region.builder()
                .code(r.code())
                .name(r.name())
                .depth(r.depth())
                .parentCode(r.parentCode())
                .pathName(r.pathName())
                .useYn(r.useYn() != null ? r.useYn() : true)
                .build();
    }

    /** 변경이 있을 때만 엔티티에 반영하고 true 반환 */
    private boolean applyIfChanged(Region target, RegionSourceProvider.Row src) {
        boolean changed = false;

        if (!Objects.equals(target.getName(), src.name()))             { target.setName(src.name()); changed = true; }
        if (!Objects.equals(target.getDepth(), src.depth()))           { target.setDepth(src.depth()); changed = true; }
        if (!Objects.equals(target.getParentCode(), src.parentCode())) { target.setParentCode(src.parentCode()); changed = true; }
        if (!Objects.equals(target.getPathName(), src.pathName()))     { target.setPathName(src.pathName()); changed = true; }

        boolean newUse = src.useYn() != null ? src.useYn() : true;
        if (target.isUseYn() != newUse)                                { target.setUseYn(newUse);    changed = true; }

        return changed;
    }

    /** DB와 비교하여 row가 신규/변경인지 판단 (소스 Row 기준) */
    private boolean isChanged(Region cur, RegionSourceProvider.Row row) {
        if (cur == null) return true;
        if (!Objects.equals(cur.getName(), row.name())) return true;
        if (!Objects.equals(cur.getDepth(), row.depth())) return true;
        if (!Objects.equals(cur.getParentCode(), row.parentCode())) return true;
        if (!Objects.equals(cur.getPathName(), row.pathName())) return true;
        boolean newUse = row.useYn() != null ? row.useYn() : true;
        return cur.isUseYn() != newUse;
    }

    // =====================================================================
    // 결과/리포트 DTO
    // =====================================================================

    /** 전체/부분 동기화 결과 */
    public record Result(int total, int inserted, int updated, int disabled) {}

    /** 누락 전체 동기화 실행 결과 요약 */
    @Getter
    public static class SyncAllReport {
        /** 전체 처리 건수 */
        private int processedTotal = 0;
        /** 배치 횟수 */
        private int batches = 0;
        /** 각 배치별 처리 건수 */
        private final List<Integer> perBatchCounts = new ArrayList<>();
        /** 총 소요 시간(ms) */
        private long elapsedMs = 0L;
        /** 사용된 prefix 목록(표시용) */
        private final List<String> prefixesUsed = new ArrayList<>();

        void addBatch(int processed) {
            this.perBatchCounts.add(processed);
            this.processedTotal += processed;
            this.batches++;
        }
        void finish(long elapsedMs) { this.elapsedMs = elapsedMs; }
    }
}
