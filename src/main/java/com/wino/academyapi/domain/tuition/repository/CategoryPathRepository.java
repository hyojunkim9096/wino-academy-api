// src/main/java/com/wino/academyapi/domain/tuition/repository/CategoryPathRepository.java
package com.wino.academyapi.domain.tuition.repository;

import com.wino.academyapi.domain.tuition.repository.projection.CategoryPathRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 카테고리 경로 계산용 네이티브 리포지토리
 * - 입력: leaf 카테고리 id 집합
 * - 출력: 각 leafId에 대한 "상위/하위/리프" 경로
 *
 * MySQL 8.x WITH RECURSIVE 사용
 */
public interface CategoryPathRepository extends Repository<com.wino.academyapi.domain.tuition.entity.StudentTuition, Long> {

    @Query(value = """
        WITH RECURSIVE cat(le_id, id, parent_id, name, path) AS (
            /* 앵커: 요청한 리프들 자체를 시작점으로 삼는다 */
            SELECT tc.id AS le_id, tc.id, tc.parent_id, tc.name, CAST(tc.name AS CHAR(512)) AS path
              FROM tuition_category tc
             WHERE tc.id IN (:ids)
            UNION ALL
            /* 위로 거슬러 올라가며 path를 앞에 붙인다(부모/부모의 부모/...) */
            SELECT cat.le_id, p.id, p.parent_id, p.name, CONCAT(p.name, '/', cat.path) AS path
              FROM tuition_category p
              JOIN cat ON cat.parent_id = p.id
        )
        /* 최상위(parent_id IS NULL)에 도달한 경로만 선택하면 full-path 완성 */
        SELECT le_id AS leafId, path
          FROM cat
         WHERE parent_id IS NULL
        """, nativeQuery = true)
    List<CategoryPathRow> findPaths(@Param("ids") Collection<Long> categoryIds);
}