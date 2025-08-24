// src/main/java/com/wino/academyapi/domain/dbconn/service/DbConnectionProfileService.java
package com.wino.academyapi.domain.dbconn.service;

import com.wino.academyapi.domain.dbconn.dto.DbConnProfileDto;
import com.wino.academyapi.domain.dbconn.entity.DbConnectionProfile;
import com.wino.academyapi.domain.dbconn.entity.DbConnectionProfileDeleted;
import com.wino.academyapi.domain.dbconn.repository.DbConnectionProfileDeletedRepository;
import com.wino.academyapi.domain.dbconn.repository.DbConnectionProfileRepository;
import com.wino.academyapi.global.crypto.AesGcmCrypto;
import com.wino.academyapi.global.datasource.DataSourceReloadService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DB 연결 프로필 서비스
 *
 * 정책:
 *  - isActive: NULL(비활성), TRUE(활성)
 *  - prod 활성화 시 → dev 전체 비활성화
 *  - dev  활성화 시 → prod 전체 비활성화
 *  - 동일 env(dev/prod) 내 복수 활성 허용
 *
 * 구현 주의:
 *  - “사전 검증 → 커밋 → (커밋 후) 스왑” 순서로 예외/커밋 충돌 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbConnectionProfileService {

    private final DbConnectionProfileRepository repo;
    private final DbConnectionProfileDeletedRepository deletedRepo;
    private final AesGcmCrypto crypto;
    private final DataSourceReloadService dsReload;

    /* ========================= 조회 ========================= */
    @Transactional(readOnly = true)
    public List<DbConnectionProfile> listAll() {
        return repo.findAllOrdered();
    }

    @Transactional(readOnly = true)
    public List<DbConnectionProfile> listByEnv(String env) {
        return repo.findByEnvCodeOrderByIsActiveDescIdAsc(env);
    }

    @Transactional(readOnly = true)
    public DbConnectionProfile get(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new EntityNotFoundException("DB profile not found: " + id));
    }

    /** env 내 현재 활성(없으면 null) — 호환 용도 */
    @Transactional(readOnly = true)
    public DbConnectionProfile findActiveInEnvOrNull(String env) {
        return repo.findFirstByEnvCodeAndIsActiveIsTrue(env).orElse(null);
    }

    /* ========================= 생성/수정/삭제/활성/비활성 ========================= */

    /** 생성 */
    @Transactional
    public DbConnectionProfile create(DbConnProfileDto dto) {
        final boolean wantActive = Boolean.TRUE.equals(dto.getIsActive());

        DbConnectionProfile p = toEntity(new DbConnectionProfile(), dto);
        p.setIsActive(null); // 기본 비활성 저장
        p = repo.save(p);

        if (wantActive) {
            // 1) 사전 검증: 접속 불가면 활성화 자체를 하지 않음(상태 불일치 방지)
            validateOrThrow(p.getId());
            // 2) 교차-환경 비활성 + 타겟 활성(스왑은 커밋 후)
            activateWithPolicy(p.getId());
        }

        log.info("[DB-CONN] create id={} env={} active={}", p.getId(), p.getEnvCode(), p.getIsActive());
        return p;
    }

    /**
     * 수정
     * - dto.isActive == true  → (사전 검증 OK 시) 교차-환경 비활성 후 타겟 활성
     * - dto.isActive == false → 단건 비활성(NULL) 후 현재 활성 기준 리로드(커밋 후)
     * - dto.isActive == null  → 활성 상태 변경 없음
     */
    @Transactional
    public DbConnectionProfile update(Long id, DbConnProfileDto dto) {
        DbConnectionProfile p = get(id);
        String oldEnc = p.getEncPassword();

        // 일반 필드 매핑 (isActive 변경은 정책 함수에서만)
        toEntity(p, dto);

        // 평문 비번 미입력 시 기존 암호 유지
        if (dto.getPasswordPlain() == null || dto.getPasswordPlain().isBlank()) {
            p.setEncPassword(oldEnc);
        }

        if (dto.getIsActive() != null) {
            if (dto.getIsActive()) {
                validateOrThrow(id);
                activateWithPolicy(id); // 스왑은 커밋 후 실행됨
            } else {
                // 멱등 비활성
                int off = repo.deactivateById(id); // 0이면 이미 비활성
                p.setIsActive(null);
                log.info("[DB-CONN] update-disable: id={} off={}", id, off);
                scheduleReloadActiveAfterCommit("update-disable");
            }
        }

        log.info("[DB-CONN] update id={} env={} active={}", p.getId(), p.getEnvCode(), p.getIsActive());
        return p; // flush by tx
    }

    /** 수동 활성화 — 교차-환경 정책 적용(사전 검증 포함) */
    @Transactional
    public void activate(Long id) {
        validateOrThrow(id);
        activateWithPolicy(id);
    }

    /**
     * 수동 비활성화
     * - 대상 1건 비활성(NULL) — 멱등(이미 비활성인 경우 0 update)
     * - 이후 현재 활성 기준으로 DS 유지/리로드 시도(커밋 후, 실패는 로그만)
     */
    @Transactional
    public void deactivate(Long id) {
        DbConnectionProfile target = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("DB profile not found: " + id));

        int off = repo.deactivateById(id); // MySQL: 동일값이면 0
        if (off == 0) {
            log.info("[DB-CONN] deactivate(idempotent): id={} already inactive", id);
        } else {
            log.info("[DB-CONN] deactivate: id={} off={}", id, off);
            target.setIsActive(null);
        }

        scheduleReloadActiveAfterCommit("deactivate");
    }

    /**
     * 교차-환경 정책(대칭):
     *  - prod 활성화 → dev 전체 비활성화, prod 대상 활성(동일 env 복수 활성 허용)
     *  - dev  활성화 → prod 전체 비활성화, dev  대상 활성(동일 env 복수 활성 허용)
     *  - 기타 env     → 교차 처리 없이 대상만 활성
     *  - on==0(이미 활성)도 멱등 처리
     *
     *  ⚠ 스왑은 즉시 호출하지 않고 "커밋 후"에 실행한다.
     */
    private void activateWithPolicy(Long id) {
        DbConnectionProfile target = repo.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("DB profile not found: " + id));
        final String env = nvl(target.getEnvCode(), "dev").toLowerCase();

        if (isProd(env)) {
            int devOff = repo.deactivateAllInEnv("dev");
            int on     = repo.activateById(id); // 이미 활성일 수 있음(0)
            log.info("[DB-CONN] activate(prod): id={} devOff={} prodOn(changed)={}", id, devOff, on);
            target.setIsActive(Boolean.TRUE);
            scheduleReloadToIdAfterCommit("activate-prod", id);
            return;
        }

        if (isDev(env)) {
            int prodOff = repo.deactivateAllInEnv("prod");
            int on      = repo.activateById(id);
            log.info("[DB-CONN] activate(dev): id={} prodOff={} devOn(changed)={}", id, prodOff, on);
            target.setIsActive(Boolean.TRUE);
            scheduleReloadToIdAfterCommit("activate-dev", id);
            return;
        }

        int on = repo.activateById(id);
        log.info("[DB-CONN] activate({}): id={} on(changed)={}", env, id, on);
        target.setIsActive(Boolean.TRUE);
        scheduleReloadToIdAfterCommit("activate-" + env, id);
    }

    /** 삭제: 활성 중이면 금지 → 삭제 이력 저장 후 삭제 */
    @Transactional
    public void delete(Long id, String deletedBy) {
        DbConnectionProfile p = get(id);
        if (Boolean.TRUE.equals(p.getIsActive())) {
            throw new IllegalStateException("활성화된 연결은 삭제할 수 없습니다. 먼저 다른 항목을 활성화하세요.");
        }
        DbConnectionProfileDeleted d = DbConnectionProfileDeleted.builder()
                .profileId(p.getId())
                .profileName(p.getProfileName())
                .envCode(p.getEnvCode())
                .jdbcUrl(p.getJdbcUrl())
                .username(p.getUsername())
                .encPassword(p.getEncPassword())
                .driverClass(p.getDriverClass())
                .maximumPoolSize(p.getMaximumPoolSize())
                .minimumIdle(p.getMinimumIdle())
                .idleTimeoutMs(p.getIdleTimeoutMs())
                .maxLifetimeMs(p.getMaxLifetimeMs())
                .isActive(p.getIsActive())
                .remark(p.getRemark())
                .deletedAt(LocalDateTime.now())
                .deletedBy(deletedBy)
                .build();
        deletedRepo.save(d);
        repo.delete(p);
    }

    /* ========================= 삭제 이력 / YML 스니펫 ========================= */

    /** env = dev | prod | all */
    @Transactional(readOnly = true)
    public List<DbConnProfileDto.Deleted> listDeleted(String env) {
        var rows = "all".equalsIgnoreCase(env)
                ? deletedRepo.findAll(Sort.by(Sort.Order.desc("deletedAt")))
                : deletedRepo.findByEnvCodeOrderByDeletedAtDesc(env);
        return rows.stream().map(d -> DbConnProfileDto.Deleted.builder()
                .id(d.getId())
                .profileId(d.getProfileId())
                .profileName(d.getProfileName())
                .envCode(d.getEnvCode())
                .jdbcUrl(d.getJdbcUrl())
                .username(d.getUsername())
                .driverClass(d.getDriverClass())
                .maximumPoolSize(d.getMaximumPoolSize())
                .minimumIdle(d.getMinimumIdle())
                .idleTimeoutMs(d.getIdleTimeoutMs())
                .maxLifetimeMs(d.getMaxLifetimeMs())
                .isActive(d.getIsActive())
                .remark(d.getRemark())
                .deletedAt(d.getDeletedAt())
                .deletedBy(d.getDeletedBy())
                .build()
        ).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String generateYmlSnippet(Long id) {
        DbConnectionProfile p = get(id);
        String masked = (p.getEncPassword() == null || p.getEncPassword().isBlank()) ? "" : "********";
        return ""
                + "# application-" + nvl(p.getEnvCode(), "dev") + ".yml\n"
                + "spring:\n"
                + "  datasource:\n"
                + "    driver-class-name: " + nvl(p.getDriverClass(), "com.mysql.cj.jdbc.Driver") + "\n"
                + "    url: \"" + nvl(p.getJdbcUrl(), "") + "\"\n"
                + "    username: \"" + nvl(p.getUsername(), "") + "\"\n"
                + "    password: \"" + masked + "\"  # ← 실제 값 입력\n"
                + "    hikari:\n"
                + "      maximum-pool-size: " + nvl(p.getMaximumPoolSize(), 10) + "\n"
                + "      minimum-idle: " + nvl(p.getMinimumIdle(), 5) + "\n"
                + "      idle-timeout: " + nvl(p.getIdleTimeoutMs(), 300_000L) + "\n"
                + "      max-lifetime: " + nvl(p.getMaxLifetimeMs(), 1_800_000L) + "\n";
    }

    /* ========================= 내부 유틸 ========================= */

    /** 일반 필드만 매핑 — isActive는 정책 함수에서만 변경 */
    private DbConnectionProfile toEntity(DbConnectionProfile p, DbConnProfileDto dto) {
        p.setProfileName(dto.getProfileName());
        p.setEnvCode(dto.getEnvCode());
        p.setJdbcUrl(dto.getJdbcUrl());
        p.setUsername(dto.getUsername());
        if (dto.getPasswordPlain() != null && !dto.getPasswordPlain().isBlank()) {
            p.setEncPassword(crypto.encrypt(dto.getPasswordPlain()));
        }
        p.setDriverClass(dto.getDriverClass());
        p.setMaximumPoolSize(dto.getMaximumPoolSize());
        p.setMinimumIdle(dto.getMinimumIdle());
        p.setIdleTimeoutMs(dto.getIdleTimeoutMs());
        p.setMaxLifetimeMs(dto.getMaxLifetimeMs());
        p.setRemark(dto.getRemark());
        return p;
    }

    /* ===== afterCommit 실행 스케줄링 + 사전 검증 유틸 ===== */

    /** 트랜잭션 커밋 후 실행. 트랜잭션이 없으면 즉시 실행 */
    private void afterCommitOrNow(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }

    private void scheduleReloadToIdAfterCommit(String tag, Long id) {
        afterCommitOrNow(() -> tryReloadToId(tag, id));
    }

    private void scheduleReloadActiveAfterCommit(String tag) {
        afterCommitOrNow(() -> tryReloadActive(tag));
    }

    /** 사전 검증: 해당 프로필로 임시 커넥션을 열어 접속 가능 여부 확인(스왑 없음) */
    private void validateOrThrow(Long profileId) {
        try {
            dsReload.validateProfileId(profileId);
        } catch (Exception e) {
            throw new IllegalStateException("타겟 DB 접속 검증 실패: " + e.getMessage());
        }
    }

    /** 새로 활성으로 바꾼 id로 스왑(예외는 로깅만) — afterCommit에서 호출됨 */
    private void tryReloadToId(String tag, Long id) {
        try {
            var r = dsReload.reloadToProfileId(id);
            log.info("[DB-CONN] reloadToId({}): {}", tag, r);
        } catch (Exception e) {
            log.warn("[DB-CONN] reloadToId({}) failed after id={}: {}", tag, id, e.toString());
        }
    }

    /** 현재 활성 기준 스왑 — afterCommit에서 호출됨 */
    private void tryReloadActive(String tag) {
        try {
            var r = dsReload.reloadFromActive();
            log.info("[DB-CONN] reloadFromActive({}): {}", tag, r);
        } catch (Exception e) {
            log.warn("[DB-CONN] reloadFromActive({}) failed: {}", tag, e.toString());
        }
    }

    private static boolean isProd(String env) { return "prod".equalsIgnoreCase(env); }
    private static boolean isDev(String env)  { return "dev".equalsIgnoreCase(env); }
    private static String nvl(String s, String def) { return (s == null || s.isBlank()) ? def : s; }
    private static int nvl(Integer v, int def) { return v == null ? def : v; }
    private static long nvl(Long v, long def) { return v == null ? def : v; }
}
