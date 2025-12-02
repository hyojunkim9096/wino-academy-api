package com.wino.academyapi.domain.course.repository;

import com.wino.academyapi.domain.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByWorkLocationCodeAndSchoolStageAndUseYnOrderBySortOrderAscNameAsc(
            String workLocationCode, String schoolStage, boolean useYn
    );

    Optional<Course> findTopByWorkLocationCodeAndSchoolStageOrderBySortOrderDesc(
            String workLocationCode, String schoolStage
    );

    List<Course> findByWorkLocationCodeAndSchoolStageAndIdIn(
            String workLocationCode, String schoolStage, List<Long> ids
    );
}