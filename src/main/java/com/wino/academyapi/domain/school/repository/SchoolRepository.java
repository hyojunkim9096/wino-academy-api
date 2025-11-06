// src/main/java/com/wino/academyapi/domain/school/repository/SchoolRepository.java
package com.wino.academyapi.domain.school.repository;

import com.wino.academyapi.domain.school.entity.School;
import com.wino.academyapi.domain.school.entity.SchoolStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 학교 리포지토리
 *
 * search(...):
 *  - admPrefix/stage/active/keyword 조건검색 + 페이징
 *  - 정렬은 Pageable Sort 로만 처리
 *
 * 외부코드 upsert 보조:
 *  - findByExternalCode, existsByExternalCode, findAllByExternalCodeIn
 *
 * 추가:
 *  - findNameById: id로 학교 이름만 조회 (StudentAdminService에서 사용)
 */
public interface SchoolRepository extends JpaRepository<School, Long> {

    @Query("""
      SELECT s FROM School s
      WHERE (:admPrefix IS NULL OR :admPrefix = '' OR s.admCode LIKE CONCAT(:admPrefix, '%'))
        AND (:stage     IS NULL OR s.stage = :stage)
        AND (:active    IS NULL OR s.active = :active)
        AND (
              :kw IS NULL OR :kw = '' OR
              s.name          LIKE CONCAT('%', :kw, '%') OR
              s.address       LIKE CONCAT('%', :kw, '%') OR
              s.detailAddress LIKE CONCAT('%', :kw, '%')
            )
    """)
    @QueryHints({
            @QueryHint(name = org.hibernate.annotations.QueryHints.READ_ONLY, value = "true"),
            @QueryHint(name = org.hibernate.annotations.QueryHints.FETCH_SIZE, value = "200")
    })
    Page<School> search(@Param("admPrefix") String admPrefix,
                        @Param("stage")     SchoolStage stage,
                        @Param("active")    Boolean active,
                        @Param("kw")        String keyword,
                        Pageable pageable);

    Optional<School> findByExternalCode(String externalCode);
    boolean existsByExternalCode(String externalCode);
    List<School> findAllByExternalCodeIn(Collection<String> externalCodes);

    /** ✅ id로 학교 이름만 조회 */
    @Query("select s.name from School s where s.id = :id")
    Optional<String> findNameById(@Param("id") Long id);
}