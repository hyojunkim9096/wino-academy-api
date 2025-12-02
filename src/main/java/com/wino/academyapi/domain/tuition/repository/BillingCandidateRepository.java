// src/main/java/com/wino/academyapi/domain/tuition/repository/BillingCandidateRepository.java
package com.wino.academyapi.domain.tuition.repository;

import com.wino.academyapi.domain.tuition.repository.projection.BillingCandidateRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Billing Console 전용 조회 리포지토리(네이티브)
 *
 * 조건:
 * - student_tuition.active = 1
 * - (선택) 학생 상태 ACTIVE만 포함
 * - (선택) 학부(stage), 지점(work_location_code) 필터
 * - 이미 해당 월에 청구가 있는지 exists 서브쿼리로 판단
 *
 * 주의:
 * - 컬럼/테이블명은 실제 DB와 맞춰야 합니다.
 * - tuition_price.enabled=1 필터 반영.
 */
public interface BillingCandidateRepository extends Repository<com.wino.academyapi.domain.tuition.entity.StudentTuition, Long> {

    @Query(value = """
        SELECT
            st.id                 AS tuitionId,
            s.id                  AS studentId,
            s.name                AS studentName,
            s.school_stage        AS schoolStage,
            s.work_location_code  AS workLocationCode,

            tp.id                 AS tuitionPriceId,
            tp.unit               AS unit,
            tp.price              AS price,
            tc.id                 AS categoryId,
            tc.name               AS categoryName,

            :month                AS billMonth,
            EXISTS(
                SELECT 1
                  FROM student_invoice inv
                 WHERE inv.student_id = s.id
                   AND inv.tuition_price_id = tp.id
                   AND inv.bill_month = :month
            ) AS alreadyBilled

        FROM student_tuition st
        JOIN student s            ON s.id = st.student_id
        JOIN tuition_price tp     ON tp.id = st.tuition_price_id AND tp.enabled = 1
        JOIN tuition_category tc  ON tc.id = tp.category_id

        WHERE st.active = 1
          AND (:onlyActive = FALSE OR UPPER(s.status) = 'ACTIVE')
          AND (:stage IS NULL OR :stage = '' OR UPPER(s.school_stage) = UPPER(:stage))
          AND (:loc   IS NULL OR :loc   = '' OR s.work_location_code = :loc)

        ORDER BY LOWER(s.name) ASC, st.id ASC
        """, nativeQuery = true)
    List<BillingCandidateRow> findCandidatesNative(
            @Param("month") String month,
            @Param("stage") String stage,
            @Param("loc") String workLocationCode,
            @Param("onlyActive") boolean onlyActive
    );
}