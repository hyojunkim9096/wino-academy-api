// ============================================================================
// src/main/java/com/wino/academyapi/domain/subject/repository/ScoreCommentSetRepository.java
// ----------------------------------------------------------------------------
// 점수 코멘트 세트 JPA 리포지토리
// - 최신 버전 1건 조회(findTopBy...OrderByVersionDesc)
// ============================================================================

package com.wino.academyapi.domain.subject.repository;

import com.wino.academyapi.domain.subject.entity.ScoreCommentSet;
import com.wino.academyapi.domain.subject.entity.ScoreCommentSet.PeriodType;
import com.wino.academyapi.domain.subject.entity.ScoreCommentSet.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** ✅ 최신 버전 1건만 필요 → Optional + findTopBy... 깔끔화 */
public interface ScoreCommentSetRepository extends JpaRepository<ScoreCommentSet, Long> {
    Optional<ScoreCommentSet> findTopBySubjectIdAndSchoolStageAndScopeTypeAndScopeRefIdAndPeriodTypeOrderByVersionDesc(
            Long subjectId, String schoolStage, ScopeType scopeType, Long scopeRefId, PeriodType periodType);
}