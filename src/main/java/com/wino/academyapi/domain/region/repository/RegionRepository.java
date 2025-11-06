// src/main/java/com/wino/academyapi/domain/region/repository/RegionRepository.java
package com.wino.academyapi.domain.region.repository;

import com.wino.academyapi.domain.region.entity.Region;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public interface RegionRepository extends JpaRepository<Region, String> {

    // ─────────────────────────────────────────────────────────────────────────────
    // ⚠️ 중요: 같은 이름의 default 오버로드 금지
    //  - findByDepthAndUseYn(int, ...) 같은 default 메서드를 두면,
    //    내부에서 (byte)로 넘겨도 자바가 byte→int 확대 변환을 더 우선시해서
    //    다시 그 default 메서드 자신이 호출되어 무한 재귀가 발생할 수 있음.
    //  - 파생 쿼리 메서드는 "정확히 하나의 시그니처"만 두고, 편의 메서드는 이름을 다르게!
    // ─────────────────────────────────────────────────────────────────────────────

    // ── [기존 유지] DB ORDER BY 포함 버전 (컨트롤러에서는 가급적 미사용; 남겨만 둠)
    List<Region> findByDepthAndUseYnOrderByNameAsc(Byte depth, boolean useYn);
    List<Region> findByParentCodeAndUseYnOrderByNameAsc(String parentCode, boolean useYn);
    List<Region> findTop50ByCodeStartingWithAndUseYnOrderByDepthAscNameAsc(String code, boolean useYn);

    // ── ✅ DB 정렬 없는 "단일 시그니처" 파생 쿼리 (Controller/Service에서 자바 정렬)
    List<Region> findByDepthAndUseYn(Byte depth, boolean useYn);
    List<Region> findByParentCodeAndUseYn(String parentCode, boolean useYn);
    List<Region> findTop50ByCodeStartingWithAndUseYn(String code, boolean useYn);

    // ── ✅ (선택) 이름이 다른 편의 메서드: null/Integer 입력을 안전하게 처리
    //      - 이름이 다르므로 파생 쿼리 메서드와 충돌/재귀 없음
    default List<Region> findAllByDepthNullable(Integer depth, boolean useYn) {
        if (depth == null) return Collections.emptyList();
        // Integer → byte → Byte (명시적 박싱으로 파생 쿼리 메서드를 확실하게 호출)
        return findByDepthAndUseYn(Byte.valueOf(depth.byteValue()), useYn);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ✅ 신규: 지역명/경로명으로 LIKE 검색 (키워드 → admPrefix 유추용)
    //  - 예: '수원시' 입력 시 depth 오름차순(1→2→3→4)으로 가장 상위 매칭을 우선 채택
    //  - DB collation 이 *_ci 이면 대소문자 무시됨 (LOWER() 미사용으로 인덱스 친화)
    //  - name 은 접두 매칭(LIKE 'kw%') → 인덱스 활용 여지, pathName 은 포함 매칭 보조
    // ─────────────────────────────────────────────────────────────────────────────
    @Query("""
        SELECT r
          FROM Region r
         WHERE r.useYn = true
           AND (
                 r.name     LIKE CONCAT(:kw, '%')
              OR r.pathName LIKE CONCAT('%', :kw, '%')
           )
         ORDER BY r.depth ASC, r.name ASC
    """)
    @QueryHints({
            @QueryHint(name = org.hibernate.annotations.QueryHints.READ_ONLY, value = "true"),
            @QueryHint(name = org.hibernate.annotations.QueryHints.FETCH_SIZE, value = "50")
    })
    Page<Region> searchByNameLike(@Param("kw") String keyword, Pageable pageable);

    // ── 이하 벌크/경량 메서드 등은 기존 그대로 ─────────────────────────
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE region
               SET use_yn = false
             WHERE use_yn = true
               AND (:hasCodes = true AND code NOT IN (:codes))
            """, nativeQuery = true)
    int bulkDisableMissing(@Param("codes") Set<String> codes, @Param("hasCodes") boolean hasCodes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "TRUNCATE TABLE region", nativeQuery = true)
    void truncate();

    @Query("select r.code from Region r")
    @QueryHints({
            @QueryHint(name = org.hibernate.annotations.QueryHints.READ_ONLY, value = "true"),
            @QueryHint(name = org.hibernate.annotations.QueryHints.FETCH_SIZE, value = "1000")
    })
    List<String> findAllCodes();

    @Query("""
           select r.code as code,
                  r.name as name,
                  r.depth as depth,
                  r.parentCode as parentCode,
                  r.pathName as pathName,
                  r.useYn as useYn
             from Region r
           """)
    @QueryHints({
            @QueryHint(name = org.hibernate.annotations.QueryHints.READ_ONLY, value = "true"),
            @QueryHint(name = org.hibernate.annotations.QueryHints.FETCH_SIZE, value = "1000")
    })
    List<RegionLight> findAllLight();

    @Query("""
           select r.code as code,
                  r.name as name,
                  r.depth as depth,
                  r.parentCode as parentCode,
                  r.pathName as pathName,
                  r.useYn as useYn
             from Region r
            where r.code in :codes
           """)
    @QueryHints({
            @QueryHint(name = org.hibernate.annotations.QueryHints.READ_ONLY, value = "true"),
            @QueryHint(name = org.hibernate.annotations.QueryHints.FETCH_SIZE, value = "1000")
    })
    List<RegionLight> findAllLightByCodeIn(@Param("codes") Set<String> codes);

    interface RegionLight {
        String getCode();
        String getName();
        Byte getDepth();
        String getParentCode();
        String getPathName();
        boolean isUseYn();
    }
}
