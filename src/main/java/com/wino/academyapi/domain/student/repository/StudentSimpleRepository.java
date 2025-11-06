// src/main/java/com/wino/academyapi/domain/student/repository/StudentSimpleRepository.java
package com.wino.academyapi.domain.student.repository;

import com.wino.academyapi.domain.student.entity.Student; // ✅ 반드시 @Entity 로 관리되는 타입
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 학생 기본 정보 조회 전용 리포지토리
 *
 * 🔧 정합화 포인트
 *  - DDL에 따라 Student 엔티티가 grade_group/grade_code를 갖습니다.
 *  - 프로젝션과 JPQL은 반드시 엔티티에 존재하는 필드만 사용해야 합니다.
 *
 * ✅ 결과
 *  - Basic 프로젝션: id, status, schoolStage, gradeGroup, gradeCode 제공
 *  - JPQL: Student 필드만 선택적으로 조회(엔티티 필드명 기준)
 */
public interface StudentSimpleRepository extends JpaRepository<Student, Long> {

    /** 최소 정보 프로젝션: 가드/청구/수강 관련 체크에 쓰이는 필드 */
    interface Basic {
        Long getId();
        String getStatus();        // 'ACTIVE' 등
        String getSchoolStage();   // 'E' | 'M' | 'H'
        String getGradeGroup();    // 'GRADE_E/M/H'
        String getGradeCode();     // 'E01' .. 'H03'
    }

    /**
     * 학생 기본정보 조회
     * - 필요 필드만 select → 불필요한 로딩 방지
     * - ⚠️ 엔티티에 없는 필드명(예: schoolGradeCode 등) 참조 금지
     */
    @Query("""
        select s.id as id,
               s.status as status,
               s.schoolStage as schoolStage,
               s.gradeGroup as gradeGroup,
               s.gradeCode as gradeCode
          from Student s
         where s.id = :id
    """)
    Optional<Basic> findBasicById(@Param("id") Long id);
}