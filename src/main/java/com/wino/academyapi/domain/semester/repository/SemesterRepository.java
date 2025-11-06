// src/main/java/com/wino/academyapi/domain/semester/repository/SemesterRepository.java
package com.wino.academyapi.domain.semester.repository;

import com.wino.academyapi.domain.semester.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 학기 레포지토리
 * - 정렬 기준: sortOrder ASC (주석대로 "작을수록 먼저")
 */
public interface SemesterRepository extends JpaRepository<Semester, Long> {

    // ── 특정 학부 ─────────────────────────────────────────────────────────
    List<Semester> findBySchoolStageAndUseYnOrderBySortOrderAsc(String schoolStage, boolean useYn);
    List<Semester> findBySchoolStageOrderBySortOrderAsc(String schoolStage);

    // ── 전체 학부 ─────────────────────────────────────────────────────────
    List<Semester> findByUseYnOrderBySortOrderAsc(boolean useYn);
    List<Semester> findAllByOrderBySortOrderAsc();
}