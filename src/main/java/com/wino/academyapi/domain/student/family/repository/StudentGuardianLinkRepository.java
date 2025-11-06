package com.wino.academyapi.domain.student.family.repository;

import com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary;
import com.wino.academyapi.domain.student.family.dto.StudentLinkSummary;
import com.wino.academyapi.domain.student.family.entity.StudentGuardianLink;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentGuardianLinkRepository extends JpaRepository<StudentGuardianLink, Long> {

    Optional<StudentGuardianLink> findByStudent_IdAndGuardian_Id(Long studentId, Long guardianId);

    List<StudentGuardianLink> findByStudent_Id(Long studentId);

    List<StudentGuardianLink> findByGuardian_Id(Long guardianId);

    /** 학생 기준: 가족(보호자)+링크 요약을 DTO로 바로 조회 */
    @Query("""
        select new com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary(
            l.id, g.id, g.name, g.phone, g.email,
            l.relationCode, l.primary, l.legalGuardian, l.receiveNotice, l.receiveBilling
        )
        from StudentGuardianLink l
        join l.guardian g
        where l.student.id = :studentId
        order by l.primary desc, lower(g.name) asc, l.id desc
    """)
    List<GuardianLinkSummary> findGuardianSummariesByStudent(@Param("studentId") Long studentId);

    /** 보호자 기준: 학생+링크 요약을 DTO로 바로 조회 */
    @Query("""
        select new com.wino.academyapi.domain.student.family.dto.StudentLinkSummary(
            l.id, s.id, s.name, s.schoolStage, s.workLocationCode, s.status,
            l.relationCode, l.primary, l.legalGuardian, l.receiveNotice, l.receiveBilling
        )
        from StudentGuardianLink l
        join l.student s
        where l.guardian.id = :guardianId
        order by l.primary desc, lower(s.name) asc, l.id desc
    """)
    List<StudentLinkSummary> findStudentSummariesByGuardian(@Param("guardianId") Long guardianId);
}