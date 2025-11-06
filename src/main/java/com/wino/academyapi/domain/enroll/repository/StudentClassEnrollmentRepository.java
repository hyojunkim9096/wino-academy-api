// src/main/java/com/wino/academyapi/domain/enroll/repository/StudentClassEnrollmentRepository.java
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
 * - 상태(status) 중심의 조회/집계(요구사항 유지)
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

    // ★ 추가: 해당 학생이 ACTIVE 배정(아무 반이나) 하나라도 갖고 있는지 빠르게 확인
    boolean existsByStudent_IdAndStatus(Long studentId, String status);
}