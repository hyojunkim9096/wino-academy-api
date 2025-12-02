// src/main/java/com/wino/academyapi/domain/tuition/repository/TuitionPriceInfoRepository.java
package com.wino.academyapi.domain.tuition.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/**
 * 가격표 스냅샷용 리포지토리
 * - unit, price, categoryId, itemLabel(UNIT 기반 라벨) 반환
 * - 실DB에 tuition_price.name 칼럼이 없더라도 동작하게 구성
 */
public interface TuitionPriceInfoRepository extends Repository<com.wino.academyapi.domain.tuition.entity.StudentTuition, Long> {

    interface PriceInfo {
        String getUnit();
        BigDecimal getPrice();
        Long getCategoryId();
        String getItemLabel();
    }

    @Query(value = """
        SELECT
            tp.unit          AS unit,
            tp.price         AS price,
            tp.category_id   AS categoryId,
            CASE
                WHEN UPPER(tp.unit) = 'MONTH'   THEN '월 수강료'
                WHEN UPPER(tp.unit) = 'TERM'    THEN '학기 수강료'
                WHEN UPPER(tp.unit) = 'SESSION' THEN '회차 수강료'
                ELSE '수강료'
            END               AS itemLabel
          FROM tuition_price tp
         WHERE tp.id = :priceId
        """, nativeQuery = true)
    PriceInfo findPriceInfo(@Param("priceId") Long priceId);
}