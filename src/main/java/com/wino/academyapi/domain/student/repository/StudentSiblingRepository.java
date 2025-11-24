// src/main/java/com/wino/academyapi/domain/student/repository/StudentSiblingRepository.java
package com.wino.academyapi.domain.student.repository;

import com.wino.academyapi.domain.student.dto.StudentSiblingDtos.SiblingLinkDto;
import com.wino.academyapi.domain.student.entity.StudentSibling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentSiblingRepository extends JpaRepository<StudentSibling, Long> {

    /**
     * Case 1: 내 ID(studentId)가 High 쪽에 있을 때
     * -> Low 쪽에 있는 학생(동생/작은ID)의 정보를 조회합니다.
     */
    @Query("""
        select new com.wino.academyapi.domain.student.dto.StudentSiblingDtos$SiblingLinkDto(
            sb.id, 
            s.id, 
            s.name, 
            s.schoolStage, 
            s.workLocationCode, 
            s.status, 
            sb.relationNote
        )
        from StudentSibling sb
        join sb.low s
        where sb.high.id = :studentId
        order by s.name
    """)
    List<SiblingLinkDto> findSiblingsAsHigh(@Param("studentId") Long studentId);

    /**
     * Case 2: 내 ID(studentId)가 Low 쪽에 있을 때
     * -> High 쪽에 있는 학생(형/누나/큰ID)의 정보를 조회합니다.
     */
    @Query("""
        select new com.wino.academyapi.domain.student.dto.StudentSiblingDtos$SiblingLinkDto(
            sb.id, 
            s.id, 
            s.name, 
            s.schoolStage, 
            s.workLocationCode, 
            s.status, 
            sb.relationNote
        )
        from StudentSibling sb
        join sb.high s
        where sb.low.id = :studentId
        order by s.name
    """)
    List<SiblingLinkDto> findSiblingsAsLow(@Param("studentId") Long studentId);
}