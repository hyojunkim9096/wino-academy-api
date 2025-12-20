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

    @Query("select l from StudentGuardianLink l join fetch l.guardian where l.student.id = :studentId")
    List<StudentGuardianLink> findByStudentId(@Param("studentId") Long studentId);

    List<StudentGuardianLink> findByGuardian_Id(Long guardianId);

    /**
     * 학생 기준: 가족(보호자)+링크 요약을 DTO로 조회
     * - JPQL 생성자 내부에는 주석을 넣으면 파싱 에러가 발생할 수 있어 제거함.
     */
    @Query("""
        select new com.wino.academyapi.domain.student.family.dto.GuardianLinkSummary(
            l.id,
            g.id,
            g.name,
            g.phone,
            g.email,
            l.relationCode,
            l.isPrimary,
            l.legalGuardian,
            l.receiveNotice,
            l.receiveBilling,
            u.id,
            u.loginId
        )
        from StudentGuardianLink l
        join l.guardian g
        left join g.endUserMap m
        left join m.user u
        where l.student.id = :studentId
        order by l.isPrimary desc, g.name asc, l.id desc
    """)
    List<GuardianLinkSummary> findGuardianSummariesByStudent(@Param("studentId") Long studentId);

    /**
     * 보호자 기준: 학생 + 링크 요약을 DTO로 조회
     * - 공통코드(CommonCode)와 조인하여 한글 명칭을 포함
     */
    @Query("""
        select new com.wino.academyapi.domain.student.family.dto.StudentLinkSummary(
            l.id,
            s.id,
            s.name,
            s.schoolStage,
            stg.name,
            s.workLocationCode,
            loc.name,
            s.status,
            st.name, 
            l.relationCode,
            rel.name,
            l.isPrimary,
            l.legalGuardian,
            l.receiveNotice,
            l.receiveBilling
        )
        from StudentGuardianLink l
        join l.student s
        left join com.wino.academyapi.domain.code.entity.CommonCode stg
            on stg.groupCode = 'SCHOOL_STAGE' and stg.code = s.schoolStage
        left join com.wino.academyapi.domain.code.entity.CommonCode loc
            on loc.groupCode = 'WORK_LOCATION' and loc.code = s.workLocationCode
        left join com.wino.academyapi.domain.code.entity.CommonCode st
            on st.groupCode = 'STUDENT_STATUS' and st.code = s.status
        left join com.wino.academyapi.domain.code.entity.CommonCode rel
            on rel.groupCode = 'FAMILY_REL' and rel.code = l.relationCode
        where l.guardian.id = :guardianId
        order by l.isPrimary desc, s.name asc, l.id desc
    """)
    List<StudentLinkSummary> findStudentSummariesByGuardian(@Param("guardianId") Long guardianId);
}