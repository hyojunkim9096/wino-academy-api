// src/main/java/com/wino/academyapi/global/datasource/DataSourceReloadService.java
package com.wino.academyapi.global.datasource;

import com.wino.academyapi.domain.dbconn.entity.DbConnectionProfile;
import com.wino.academyapi.domain.dbconn.repository.DbConnectionProfileRepository;
import com.wino.academyapi.global.crypto.AesGcmCrypto;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceReloadService {

    private final LazySwapDataSource swapDs;
    private final DbConnectionProfileRepository profileRepo;
    private final AesGcmCrypto crypto;
    private final Environment env;

    // ✅ 드라이버/히카리 타임아웃(검증 지연 방지)
    private static final long CONNECT_TIMEOUT_MS    = 8_000L;
    private static final long SOCKET_TIMEOUT_MS     = 8_000L;
    private static final long VALIDATION_TIMEOUT_MS = 5_000L;
    private static final long INIT_FAIL_TIMEOUT_MS  = 8_000L;

    /* ========== 공개 API (모두 트랜잭션 비참여) ========== */

    /** envCode가 비었으면 "현재 활성 우선 규칙"으로, 있으면 해당 env의 '가장 낮은 id의 활성'로 스왑 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReloadResult reload(String envCode) throws Exception {
        if (envCode == null || envCode.trim().isEmpty()) return reloadFromActive();
        return reloadByEnv(envCode.trim());
    }

    /**
     * ✅ 전역 활성 기준 리로드
     * - 우선순위:
     *   1) spring.profiles.active 로 감지한 env 에서 활성 목록이 있으면 → id 오름차순 첫 항목 선택
     *   2) 없으면 전역에서 임의 활성 1건(findFirstByIsActiveIsTrue) 사용(기존 호환)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReloadResult reloadFromActive() throws Exception {
        String preferredEnv = detectActiveEnv();
        List<DbConnectionProfile> activesInPreferred =
                profileRepo.findByEnvCodeAndIsActiveIsTrueOrderByIdAsc(preferredEnv);

        if (!activesInPreferred.isEmpty()) {
            return swapToProfile(activesInPreferred.get(0));
        }

        DbConnectionProfile anyActive = profileRepo.findFirstByIsActiveIsTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "활성 프로필이 없습니다. (전역/폴백 모두 실패) preferredEnv=" + preferredEnv));
        return swapToProfile(anyActive);
    }

    /**
     * ✅ env 기준 리로드
     * - 동일 env 내 다중 활성 허용 정책에 맞춰 "가장 낮은 id"를 선택(결정적)
     * - 활성 항목이 하나도 없으면 404 성격의 예외
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReloadResult reloadByEnv(String envCode) throws Exception {
        List<DbConnectionProfile> actives =
                profileRepo.findByEnvCodeAndIsActiveIsTrueOrderByIdAsc(envCode);
        if (actives.isEmpty()) {
            throw new IllegalStateException("활성 프로필이 없습니다. env=" + envCode);
        }
        return swapToProfile(actives.get(0));
    }

    /** 현재 스왑된 풀 정보(디버깅용) */
    public String current() { return swapDs.currentInfo(); }

    /* ========== ID 기반 검증/스왑 (트랜잭션 비참여) ========== */

    /** ✅ 스왑 없이 “해당 프로필로 접속 검증만” 수행(성공 시 조용히 종료, 실패 시 예외) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void validateProfileId(Long profileId) throws Exception {
        DbConnectionProfile p = profileRepo.findById(profileId)
                .orElseThrow(() -> new IllegalStateException("profile not found: id=" + profileId));
        HikariDataSource ds = buildHikari(p, decrypt(p.getEncPassword()));
        try (ds; Connection c = ds.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName();
            log.info("[DS] Validate only OK. product={}, url={}", product, p.getJdbcUrl());
        }
    }

    /** ✅ 지정 프로필 ID로 즉시 새 풀 스왑(검증 포함) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReloadResult reloadToProfileId(Long profileId) throws Exception {
        DbConnectionProfile p = profileRepo.findById(profileId)
                .orElseThrow(() -> new IllegalStateException("profile not found: id=" + profileId));
        return swapToProfile(p);
    }

    /* ========== 내부 구현 ========== */

    /**
     * 새 풀 구성 → 1회 실연결 검증 → 안전 스왑
     * - 검증 실패 시 newPool 즉시 close 후 예외 전파
     */
    private ReloadResult swapToProfile(DbConnectionProfile p) throws Exception {
        HikariDataSource newPool = buildHikari(p, decrypt(p.getEncPassword()));

        // 1) 사전 검증(실 연결) — 타임아웃 강제
        try (Connection c = newPool.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName();
            log.info("[DS] Validation success. product={}, url={}", product, p.getJdbcUrl());
        } catch (Exception e) {
            try { newPool.close(); } catch (Exception ignore) {}
            throw e;
        }

        // 2) 안전 스왑
        String before = swapDs.currentInfo();
        try {
            swapDs.swap(newPool, "admin-reload@" + LocalDateTime.now());
        } catch (Exception swapErr) {
            try { newPool.close(); } catch (Exception ignore) {}
            throw swapErr;
        }

        return ReloadResult.builder()
                .env(p.getEnvCode())
                .profileId(p.getId())
                .before(before)
                .after(swapDs.currentInfo())
                .appliedAt(LocalDateTime.now().toString())
                .build();
    }

    private String detectActiveEnv() {
        String[] actives = env.getActiveProfiles();
        if (actives != null && actives.length > 0) {
            log.info("[DS] spring.profiles.active={}", Arrays.toString(actives));
            return actives[0];
        }
        log.warn("[DS] spring.profiles.active 비어 있음. 기본 dev 사용");
        return "dev";
    }

    private String decrypt(String enc) {
        if (enc == null || enc.isBlank()) return "";
        return crypto.decrypt(enc);
    }

    private HikariDataSource buildHikari(DbConnectionProfile p, String plainPassword) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(p.getJdbcUrl());
        cfg.setUsername(p.getUsername());
        cfg.setPassword(plainPassword != null ? plainPassword : "");

        cfg.setDriverClassName(
                (p.getDriverClass() != null && !p.getDriverClass().isBlank())
                        ? p.getDriverClass()
                        : "com.mysql.cj.jdbc.Driver"
        );

        // ⏱ 타임아웃/연결 특성
        cfg.setConnectionTimeout(CONNECT_TIMEOUT_MS);
        cfg.setValidationTimeout(VALIDATION_TIMEOUT_MS);
        cfg.setInitializationFailTimeout(INIT_FAIL_TIMEOUT_MS);
        cfg.addDataSourceProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        cfg.addDataSourceProperty("socketTimeout",  String.valueOf(SOCKET_TIMEOUT_MS));
        cfg.addDataSourceProperty("tcpKeepAlive",   "true");

        // 히카리 풀 파라미터(프로필 값 우선)
        if (p.getMinimumIdle() != null)     cfg.setMinimumIdle(p.getMinimumIdle());
        if (p.getMaximumPoolSize() != null) cfg.setMaximumPoolSize(p.getMaximumPoolSize());
        if (p.getIdleTimeoutMs() != null)   cfg.setIdleTimeout(p.getIdleTimeoutMs());
        if (p.getMaxLifetimeMs() != null)   cfg.setMaxLifetime(p.getMaxLifetimeMs());

        cfg.setPoolName("WinoPool-" + p.getEnvCode() + "-" + p.getId() + "-" + System.currentTimeMillis());
        return new HikariDataSource(cfg);
    }

    @Data @Builder
    public static class ReloadResult {
        private String env;
        private Long profileId;
        private String before;
        private String after;
        private String appliedAt;
    }
}
