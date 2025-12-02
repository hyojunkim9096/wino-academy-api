package com.wino.academyapi.domain.course.repository;

import com.wino.academyapi.domain.course.entity.CourseSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CourseSubjectRepository extends JpaRepository<CourseSubject, Long> {

    List<CourseSubject> findByCourseIdOrderBySortOrderAsc(Long courseId);

    interface CourseSubjectRow {
        Long getId();
        Long getCourseId();
        Long getSubjectId();
        String getSubjectName();
        Long getTeacherId();
        int getSortOrder();
        boolean getUseYn();
    }

    @Query(value = """
        SELECT cs.id,
               cs.class_id       AS courseId,
               cs.subject_id     AS subjectId,
               s.name            AS subjectName,
               cs.teacher_id     AS teacherId,
               cs.sort_order     AS sortOrder,
               cs.use_yn         AS useYn
          FROM class_subject cs
          JOIN subject s ON s.id = cs.subject_id
         WHERE cs.class_id = :courseId
         ORDER BY cs.sort_order ASC
    """, nativeQuery = true)
    List<CourseSubjectRow> findRowsWithSubject(@Param("courseId") Long courseId);

    interface CsBrief {
        Long getId();
        Long getCourseId();
        String getCourseName();
        String getWorkLocationCode();
        Long getSubjectId();
        String getSubjectName();
    }

    @Query(value = """
        SELECT cs.id          AS id,
               cm.id          AS courseId,
               cm.name        AS courseName,
               cm.work_location_code AS workLocationCode,
               s.id           AS subjectId,
               s.name         AS subjectName
          FROM class_subject cs
          JOIN class_master cm ON cm.id = cs.class_id
          JOIN subject      s  ON s.id  = cs.subject_id
         WHERE cs.id IN (:ids)
    """, nativeQuery = true)
    List<CsBrief> findBriefsByIdIn(@Param("ids") Collection<Long> ids);
}