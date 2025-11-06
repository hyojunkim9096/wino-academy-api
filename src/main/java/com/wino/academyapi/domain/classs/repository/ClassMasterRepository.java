package com.wino.academyapi.domain.classs.repository;

import com.wino.academyapi.domain.classs.entity.ClassMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** class_master 리포지토리 */
public interface ClassMasterRepository extends JpaRepository<ClassMaster, Long> {

    /** 운영용 목록: 지점/학부/사용여부로 필터 + 정렬(sort_order ASC, name ASC) */
    List<ClassMaster> findByWorkLocationCodeAndSchoolStageAndUseYnOrderBySortOrderAscNameAsc(
            String workLocationCode, String schoolStage, boolean useYn
    );

    /** 같은 파티션에서 sort_order 최대값 조회용 (신규 생성 시 next sort 계산) */
    Optional<ClassMaster> findTopByWorkLocationCodeAndSchoolStageOrderBySortOrderDesc(
            String workLocationCode, String schoolStage
    );

    /** 파티션+ID 집합으로 조회 (재정렬 검증/업데이트용) */
    List<ClassMaster> findByWorkLocationCodeAndSchoolStageAndIdIn(
            String workLocationCode, String schoolStage, List<Long> ids
    );
}