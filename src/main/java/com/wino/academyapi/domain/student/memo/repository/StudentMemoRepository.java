// src/main/java/com/wino/academyapi/domain/student/memo/repository/StudentMemoRepository.java
package com.wino.academyapi.domain.student.memo.repository;

import com.wino.academyapi.domain.student.memo.entity.StudentMemo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentMemoRepository extends JpaRepository<StudentMemo, Long> {

    // 최신 1건 (상단고정 우선 → 시각 내림차순)
    Optional<StudentMemo> findTopByStudent_IdOrderByPinnedDescCreatedAtDesc(Long studentId);

    // 목록 (페이지네이션, 상단고정 우선 → 시각 내림차순)
    Page<StudentMemo> findByStudent_IdOrderByPinnedDescCreatedAtDesc(Long studentId, Pageable pageable);
}