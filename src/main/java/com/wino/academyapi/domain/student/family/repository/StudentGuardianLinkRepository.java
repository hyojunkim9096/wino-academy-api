// src/main/java/com/wino/academyapi/domain/student/family/repository/StudentGuardianLinkRepository.java
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

    /** * 학생 기준: 가족(보호자)+링크 요약을 DTO로 바로 조회
     * ✅ [수정] LEFT JOIN으로 EndUserGuardianMap(m), EndUser(u)를 조인하여
     * 계정 ID(userId)와 로그인 ID(loginId)를 추가로 가져옵니다.
     * (g.endUserMap은 엔티티 연관관계입니다)
     */
    @Query("""
        select new com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary(
            l.id, g.id, g.name, g.phone, g.email,
            l.relationCode, l.primary, l.legalGuardian, l.receiveNotice, l.receiveBilling,
            u.id, u.loginId
        )
        from StudentGuardianLink l
        join l.guardian g
        left join g.endUserMap m
        left join m.user u
        where l.student.id = :studentId
        order by l.primary desc, lower(g.name) asc, l.id desc
    """)
    List<GuardianLinkSummary> findGuardianSummariesByStudent(@Param("studentId") Long studentId);

    /** * 보호자 기준: 학생+링크 요약을 DTO로 바로 조회
     * (이 쿼리는 학생의 계정이 아닌, 학생의 프로필을 보여주므로 수정이 필요 없습니다.)
     */
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