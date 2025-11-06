package com.wino.academyapi.domain.tuition.repository;

import com.wino.academyapi.domain.tuition.entity.TuitionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.*;

public interface TuitionCategoryRepository extends JpaRepository<TuitionCategory, Long> {

    @Query("""
        select c from TuitionCategory c
         where c.schoolStage = :stage
           and ( (:parentId is null and c.parentId is null) or c.parentId = :parentId )
         order by c.sortOrder asc, c.id asc
    """)
    List<TuitionCategory> findByStageAndParent(String stage, Long parentId);

    Optional<TuitionCategory> findBySchoolStageAndCode(String stage, String code);

    boolean existsByParentId(Long parentId);

    List<TuitionCategory> findBySchoolStageOrderBySortOrderAscIdAsc(String stage);
}