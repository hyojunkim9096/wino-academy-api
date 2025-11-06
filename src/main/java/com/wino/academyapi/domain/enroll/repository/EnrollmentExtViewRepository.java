// src/main/java/com/wino/academyapi/domain/enroll/repository/EnrollmentExtViewRepository.java
package com.wino.academyapi.domain.enroll.repository;

import com.wino.academyapi.domain.enroll.entity.StudentClassEnrollment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 뷰: student_enrollment_ext_v 읽기 전용 리포지토리
 *
 * - 네이티브 쿼리 + 인터페이스 프로젝션
 * - 컬럼 alias ↔ 인터페이스 메서드명이 정확히 일치해야 매핑됩니다.
 * - Spring Data 컴포넌트 스캔으로 Repository 빈이 등록됩니다.
 */
public interface EnrollmentExtViewRepository extends Repository<StudentClassEnrollment, Long> {

    /**
     * 특정 학생의 확장 배정 목록(최신 시작일 우선)
     */
    @Query(value = """
        SELECT
          v.id                      AS id,
          v.student_id              AS studentId,
          v.class_id                AS classId,
          v.enrolled_at             AS enrolledAt,
          v.left_at                 AS leftAt,
          v.status                  AS status,
          v.class_status_code       AS classStatusCode,
          v.class_status_name       AS classStatusName,
          v.attend_days_mask        AS attendDaysMask,
          v.attend_days_label       AS attendDaysLabel,
          v.attend_days_updated_at  AS attendDaysUpdatedAt,
          v.memo                    AS memo,
          v.created_at              AS createdAt,
          v.updated_at              AS updatedAt,
          v.updated_by              AS updatedBy
        FROM student_enrollment_ext_v v
        WHERE v.student_id = :studentId
        ORDER BY v.enrolled_at DESC, v.id DESC
    """, nativeQuery = true)
    List<EnrollmentExtRow> findByStudentId(@Param("studentId") Long studentId);

    /**
     * 인터페이스 기반 프로젝션 — alias와 이름 일치해야 함
     */
    interface EnrollmentExtRow {
        Long getId();
        Long getStudentId();
        Long getClassId();
        LocalDate getEnrolledAt();
        LocalDate getLeftAt();
        String getStatus();
        String getClassStatusCode();
        String getClassStatusName();
        Integer getAttendDaysMask();
        String getAttendDaysLabel();
        LocalDateTime getAttendDaysUpdatedAt();
        String getMemo();
        LocalDateTime getCreatedAt();
        LocalDateTime getUpdatedAt();
        Long getUpdatedBy();
    }
}