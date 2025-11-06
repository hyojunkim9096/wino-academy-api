// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/repository/ScoreCommentBandRepository.java
// ----------------------------------------------------------------------------
// 점수 밴드 JPA 리포지토리
// - 세트(comment_set_id) 기준 정렬 조회
// ============================================================================

package com.wino.academyapi.domain.subject.repository;

import com.wino.academyapi.domain.subject.entity.ScoreCommentBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScoreCommentBandRepository extends JpaRepository<ScoreCommentBand, Long> {
    List<ScoreCommentBand> findByCommentSetIdOrderBySortOrderAsc(Long setId);
}