package com.wino.academyapi.domain.course.repository;

import com.wino.academyapi.domain.course.entity.CourseTimeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Time;
import java.util.List;

public interface CourseTimeslotRepository extends JpaRepository<CourseTimeslot, Long> {

    List<CourseTimeslot> findByCourseSubjectIdOrderByDayOfWeekAscStartTimeAsc(Long courseSubjectId);

    void deleteByCourseSubjectId(Long courseSubjectId);

    interface BusyProjection {
        Long   getCourseId();
        String getCourseName();
        String getWorkLocationCode();
        Long   getCourseSubjectId();
        Long   getTeacherId();
        Integer getDayOfWeek();
        Time    getStartTime();
        Time    getEndTime();
        Long   getSubjectId();
        String getSubjectName();
    }

    @Query(value = """
        SELECT cm.id                 AS courseId,
               cm.name               AS courseName,
               cm.work_location_code AS workLocationCode,
               cs.id                 AS courseSubjectId,
               cs.teacher_id         AS teacherId,
               t.day_of_week         AS dayOfWeek,
               t.start_time          AS startTime,
               t.end_time            AS endTime,
               s.id                  AS subjectId,
               s.name                AS subjectName
          FROM class_timeslot t
          JOIN class_subject  cs ON cs.id = t.class_subject_id
          JOIN class_master   cm ON cm.id = cs.class_id
          JOIN subject        s  ON s.id  = cs.subject_id
         WHERE cs.teacher_id = :teacherId
           AND t.day_of_week = :dayOfWeek
           AND COALESCE(t.use_yn,1)=1
           AND COALESCE(cm.use_yn,1)=1
           AND t.start_time < :endTime
           AND :startTime    < t.end_time
           AND cs.id <> :excludeCsId
    """, nativeQuery = true)
    List<BusyProjection> findConflictsForTeacher(
            @Param("teacherId") Long teacherId,
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("startTime") Time startTime,
            @Param("endTime")   Time endTime,
            @Param("excludeCsId") Long excludeCsId
    );

    @Query(value = """
        SELECT t.id
          FROM class_timeslot t
          JOIN class_subject  cs ON cs.id = t.class_subject_id
          JOIN class_master   cm ON cm.id = cs.class_id
         WHERE cm.id = :courseId
           AND COALESCE(t.use_yn,1)=1
           AND COALESCE(cm.use_yn,1)=1
         ORDER BY t.day_of_week ASC, t.start_time ASC
    """, nativeQuery = true)
    List<Long> findActiveTimeslotIdsByCourseId(@Param("courseId") Long courseId);
}