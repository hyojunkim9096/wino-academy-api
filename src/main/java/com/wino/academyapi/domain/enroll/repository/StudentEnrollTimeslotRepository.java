package com.wino.academyapi.domain.enroll.repository;

import com.wino.academyapi.domain.enroll.entity.StudentEnrollTimeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StudentEnrollTimeslotRepository extends JpaRepository<StudentEnrollTimeslot, Long> {

    // ✅ 필드명 변경(enrollment)에 따라 메서드명도 변경
    void deleteByEnrollmentId(Long enrollId);

    // ✅ 필드명 변경에 따라 메서드명도 변경
    List<StudentEnrollTimeslot> findByEnrollmentId(Long enrollId);

    /**
     * 학생의 '현재 수강 중인(ACTIVE)' 타임슬롯 ID 목록 조회
     * - setl.enrollment 필드를 통해 조인 수행
     */
    @Query("""
        SELECT setl.timeslotId
          FROM StudentEnrollTimeslot setl
          JOIN setl.enrollment e
         WHERE e.student.id = :studentId
           AND e.status = 'ACTIVE'
           AND (:excludeEnrollId IS NULL OR e.id <> :excludeEnrollId)
    """)
    List<Long> findActiveTimeslotIdsByStudent(
            @Param("studentId") Long studentId,
            @Param("excludeEnrollId") Long excludeEnrollId
    );
}