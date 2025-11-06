// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/repository/SubjectItemRepository.java
// ----------------------------------------------------------------------------
// 과목 평가 항목 JPA 리포지토리
// - 한 과목 내 정렬순 조회
// ============================================================================

package com.wino.academyapi.domain.subject.repository;

import com.wino.academyapi.domain.subject.entity.SubjectItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectItemRepository extends JpaRepository<SubjectItem, Long> {
    List<SubjectItem> findBySubjectIdOrderBySortOrderAsc(Long subjectId);
}