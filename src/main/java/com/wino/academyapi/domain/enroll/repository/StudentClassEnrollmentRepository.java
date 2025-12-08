package com.wino.academyapi.domain.enroll.repository;

import com.wino.academyapi.domain.enroll.entity.StudentClassEnrollment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 학생 반 배정 리포지토리
 *
 * - 상태(status) 중심의 조회/집계
 * - 현재원 집계는 status='ACTIVE' 기준이며 MAIN/CROSS 구분 없음
 */
public interface StudentClassEnrollmentRepository extends JpaRepository<StudentClassEnrollment, Long> {

    /**
     * 특정 학생의 배정 목록 (최근 배정일 우선)
     */
    @Query("""
        select e from StudentClassEnrollment e
         where e.student.id = :sid
         order by e.enrolledAt desc, e.id desc
    """)
    Page<StudentClassEnrollment> findByStudent(@Param("sid") Long studentId, Pageable pageable);

    /* ========================= 현재원 집계 ========================= */

    /** classId 별 ACTIVE 개수 반환용 Projection */
    interface ClassActiveCount {
        Long getClassId();
        Long getCnt();
    }

    /**
     * classIds 묶음에 대한 ACTIVE 건수 집계
     * - WHERE e.status = 'ACTIVE' AND e.classId in (:classIds)
     * - 인덱스: idx_enroll_class(class_id, status) 활용
     */
    @Query("""
        select e.classId as classId, count(e.id) as cnt
          from StudentClassEnrollment e
         where e.status = 'ACTIVE' and e.classId in :classIds
         group by e.classId
    """)
    List<ClassActiveCount> countActiveByClassIds(@Param("classIds") List<Long> classIds);

    /* ========================= 중복 ACTIVE 가드 ========================= */

    /** 동일 학생·반 조합에 ACTIVE 배정 존재 여부 (생성 시) */
    boolean existsByStudent_IdAndClassIdAndStatus(Long studentId, Long classId, String status);

    /** 동일 학생·반 조합에 (자기 자신 제외) ACTIVE 존재 여부 (수정 시) */
    @Query("""
        select (count(e) > 0) from StudentClassEnrollment e
         where e.student.id = :sid
           and e.classId = :cid
           and e.status = 'ACTIVE'
           and e.id <> :excludeId
    """)
    boolean existsAnotherActive(@Param("sid") Long studentId,
                                @Param("cid") Long classId,
                                @Param("excludeId") Long excludeId);

    /** * ★ 추가: 해당 학생이 ACTIVE 배정(아무 반이나) 하나라도 갖고 있는지 빠르게 확인
     */
    boolean existsByStudent_IdAndStatus(Long studentId, String status);

    /**
     * ✅ [핵심 추가] 특정 학기에 이미 활성(ACTIVE) 상태인 메인(MAIN) 배정이 존재하는지 확인
     * - Course(class_master)와 조인하여 학기(semester_id)를 비교
     * - excludeEnrollId가 null이 아니면 해당 ID는 제외하고 검사 (수정 시 사용)
     */
    @Query("""
        SELECT COUNT(e) > 0
          FROM StudentClassEnrollment e
          JOIN Course c ON e.classId = c.id
         WHERE e.student.id = :studentId
           AND c.semesterId = :semesterId
           AND e.status = 'ACTIVE'
           AND e.classStatusCode = 'MAIN'
           AND (:excludeEnrollId IS NULL OR e.id <> :excludeEnrollId)
    """)
    boolean existsActiveMainInSemester(
            @Param("studentId") Long studentId,
            @Param("semesterId") Long semesterId,
            @Param("excludeEnrollId") Long excludeEnrollId
    );
}