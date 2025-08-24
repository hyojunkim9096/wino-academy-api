// src/main/java/com/wino/academyapi/domain/dbconn/repository/DbConnectionProfileRepository.java
package com.wino.academyapi.domain.dbconn.repository;

import com.wino.academyapi.domain.dbconn.entity.DbConnectionProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * DB 연결 프로필 Repository
 *
 * 정책(2025-08 개정):
 *  - ✅ prod 활성화 시 → dev 전체 비활성화
 *  - ✅ dev  활성화 시 → prod 전체 비활성화
 *  - ✅ 같은 env(dev/prod) 내에서는 "복수 활성 허용"
 *
 * 정렬(통합 목록 화면):
 *  - envCode ASC → 활성(TRUE) 먼저 → id ASC
 */
public interface DbConnectionProfileRepository extends JpaRepository<DbConnectionProfile, Long> {

    /* ───────── 조회 ───────── */

    /** env별 목록(기존/호환) */
    List<DbConnectionProfile> findByEnvCodeOrderByIdAsc(String envCode);

    /** env별 목록: 활성 우선 정렬(목록 UI에 편리) */
    List<DbConnectionProfile> findByEnvCodeOrderByIsActiveDescIdAsc(String envCode);

    /** env별 활성 목록(복수 활성 허용 정책에 맞춰 추가) */
    List<DbConnectionProfile> findByEnvCodeAndIsActiveIsTrueOrderByIdAsc(String envCode);

    /** 통합 목록: envCode ↑, 활성(TRUE) 먼저, id ↑ */
    @Query("""
        select p
          from DbConnectionProfile p
         order by p.envCode asc,
                  case when p.isActive = true then 0 else 1 end,
                  p.id asc
    """)
    List<DbConnectionProfile> findAllOrdered();

    /** (호환) 특정 env의 활성 1건 — 과거 단일 활성 정책 잔재. 이제는 다수일 수 있으므로 주로 "샘플" 용도 */
    Optional<DbConnectionProfile> findFirstByEnvCodeAndIsActiveIsTrue(String envCode);

    /** 잠금 조회(경쟁조건 방지: 서비스에서 @Transactional과 함께 사용) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DbConnectionProfile p where p.id = :id")
    Optional<DbConnectionProfile> findByIdForUpdate(@Param("id") Long id);

    /* ───────── 활성/비활성 갱신 ───────── */

    /** 대상 1건 활성(TRUE) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DbConnectionProfile p set p.isActive = true where p.id = :id")
    int activateById(@Param("id") Long id);

    /** 대상 1건 비활성(NULL) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DbConnectionProfile p set p.isActive = null where p.id = :id")
    int deactivateById(@Param("id") Long id);

    /** 특정 env 전체 비활성화(NULL) — 교차-환경 정책 구현에 사용(dev↔prod) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DbConnectionProfile p set p.isActive = null where p.envCode = :env")
    int deactivateAllInEnv(@Param("env") String envCode);

    /* ───────── (옵션) 전역 스위치/점검용 ───────── */

    /** 전역에서 활성 1건(있다면) — 점검/폴백용(복수 활성 허용 정책과 무관) */
    Optional<DbConnectionProfile> findFirstByIsActiveIsTrue();

    /** 전역 전체 비활성(NULL) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DbConnectionProfile p set p.isActive = null")
    int deactivateAll();

    /** 지정 id 제외 전부 비활성(NULL) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DbConnectionProfile p set p.isActive = null where p.id <> :id")
    int deactivateAllExcept(@Param("id") Long id);

    /* ───────── (폐기 예정) 단일-활성 강제 관련 ─────────
       이전 정책(동일 env 단일 활성)에서 사용되던 메서드입니다.
       현재 정책에서는 사용하지 않으며, 혹시 남아있는 호출이 있으면 제거 권장.
    */

    /** [Deprecated] 동일 env에서 '나 제외' 비활성 — 복수 활성 허용 정책에서는 사용 금지 */
    @Deprecated
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DbConnectionProfile p set p.isActive = null where p.envCode = :env and p.id <> :id")
    int deactivateOthersInEnv(@Param("env") String envCode, @Param("id") Long id);

    /** [Deprecated] env 내 활성 개수 — 단일 활성 정책 점검용 */
    @Deprecated
    long countByEnvCodeAndIsActiveIsTrue(String envCode);
}
