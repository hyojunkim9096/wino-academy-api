package com.wino.academyapi.domain.classs.repository;

import com.wino.academyapi.domain.classs.entity.ClassTimeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Time;
import java.util.List;

/**
 * 반 타임슬롯 리포지토리
 *
 * 운영 기준:
 *  - 충돌 검사의 경우와 동일하게 timeslot(use_yn) + class_master(use_yn)만 신뢰
 *  - class_subject.use_yn 은 데이터 품질 이슈로 신뢰하지 않음(조회/충돌 동일 기준 유지)
 */
public interface ClassTimeslotRepository extends JpaRepository<ClassTimeslot, Long> {

    List<ClassTimeslot> findByClassSubjectIdOrderByDayOfWeekAscStartTimeAsc(Long classSubjectId);

    interface BusyProjection {
        Long   getClassId();
        String getClassName();
        String getWorkLocationCode();
        Long   getClassSubjectId();
        Long   getTeacherId();
        Integer getDayOfWeek();
        Time    getStartTime();
        Time    getEndTime();
        Long   getSubjectId();
        String getSubjectName();
    }

    /**
     * 같은 요일 & 겹치는 시간대(동시간대 포함) 조회
     *  - self(excludeCsId) 제외
     *  - 활성 판단은 timeslot(use_yn)과 class_master(use_yn)만 사용
     *    ※ class_subject.use_yn은 데이터 상 0인 경우가 있어 충돌이 누락되어 제외
     */
    @Query(value = """
        SELECT cm.id                 AS classId,
               cm.name               AS className,
               cm.work_location_code AS workLocationCode,
               cs.id                 AS classSubjectId,
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
           AND COALESCE(t.use_yn,1)=1           -- 슬롯 활성만 신뢰
           AND COALESCE(cm.use_yn,1)=1          -- 반 자체가 비활성인 경우만 제외
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

    void deleteByClassSubjectId(Long classSubjectId);

    /* ============================================================
     * ✅ MAIN 배정 자동 연결용 — 반의 "활성" 타임슬롯 ID 목록 조회
     *  - 기준: class_master.use_yn, class_timeslot.use_yn
     *  - class_subject.use_yn 은 신뢰하지 않음(충돌 쿼리와 동일 정책)
     *  - 필요 시 시간/기간 컬럼이 있다면 조건을 추가해서 as-of 필터링 가능
     * ============================================================ */
    @Query(value = """
        SELECT t.id
          FROM class_timeslot t
          JOIN class_subject  cs ON cs.id = t.class_subject_id
          JOIN class_master   cm ON cm.id = cs.class_id
         WHERE cm.id = :classId
           AND COALESCE(t.use_yn,1)=1
           AND COALESCE(cm.use_yn,1)=1
         ORDER BY t.day_of_week ASC, t.start_time ASC
    """, nativeQuery = true)
    List<Long> findActiveTimeslotIdsByClassId(@Param("classId") Long classId);
}