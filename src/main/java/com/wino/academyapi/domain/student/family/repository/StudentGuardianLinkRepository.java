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

    /**
     * 학생 기준: 가족(보호자)+링크 요약을 DTO로 바로 조회
     * - GuardianLinkSummary: 보호자 + 계정(ID) + 링크 플래그
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

    /**
     * 보호자 기준: 학생 + 링크 요약을 DTO로 바로 조회
     * - ✅ [수정] 공통코드(CommonCode) 조인으로
     *   학부/지점/상태/관계의 "코드 + 이름"을 함께 가져옵니다.
     *
     * group_code 값은 DB 공통코드 규칙에 맞춰 사용:
     *   - SCHOOL_STAGE   : 학부
     *   - WORK_LOCATION  : 소속관
     *   - STUDENT_STATUS : 학생 상태
     *   - FAMILY_REL     : 가족 관계
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
            l.primary,
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
        order by l.primary desc, lower(s.name) asc, l.id desc
    """)
    List<StudentLinkSummary> findStudentSummariesByGuardian(@Param("guardianId") Long guardianId);
}
