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
     * studentId가 high_id에 있는 경우, low_id에 연결된 '동생' 학생의 정보를 조회합니다.
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
     * studentId가 low_id에 있는 경우, high_id에 연결된 '형' 학생의 정보를 조회합니다.
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