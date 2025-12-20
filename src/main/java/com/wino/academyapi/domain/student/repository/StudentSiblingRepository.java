package com.wino.academyapi.domain.student.repository;

import com.wino.academyapi.domain.student.dto.StudentSiblingDtos.SiblingLinkDto;
import com.wino.academyapi.domain.student.entity.StudentSibling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentSiblingRepository extends JpaRepository<StudentSibling, Long> {

    /* ========================= 로직용 (Entity 반환) ========================= */

    /**
     * 나(studentId)와 연결된 모든 형제 관계 조회
     * - low 자리에 있거나 high 자리에 있는 경우 모두 조회
     * - join fetch를 사용하여 Student 객체까지 한 번에 로딩 (N+1 방지)
     */
    @Query("select sb from StudentSibling sb " +
            "join fetch sb.low " +
            "join fetch sb.high " +
            "where sb.low.id = :sid or sb.high.id = :sid")
    List<StudentSibling> findAllByStudentId(@Param("sid") Long studentId);

    /**
     * 두 학생 간의 연결 여부 확인 (중복 방지)
     */
    @Query("select count(sb) > 0 from StudentSibling sb where sb.low.id = :low and sb.high.id = :high")
    boolean existsConnection(@Param("low") Long lowId, @Param("high") Long highId);

    /**
     * 두 학생 간의 연결 해제
     */
    @Modifying
    @Query("delete from StudentSibling sb where sb.low.id = :low and sb.high.id = :high")
    void deleteConnection(@Param("low") Long lowId, @Param("high") Long highId);


    /* ========================= 화면용 (DTO 반환) ========================= */

    @Query("""
        select new com.wino.academyapi.domain.student.dto.StudentSiblingDtos$SiblingLinkDto(
            sb.id, s.id, s.name, s.schoolStage, s.workLocationCode, s.status, sb.relationNote
        )
        from StudentSibling sb
        join sb.low s
        where sb.high.id = :studentId
        order by s.name
    """)
    List<SiblingLinkDto> findSiblingsAsHigh(@Param("studentId") Long studentId);

    @Query("""
        select new com.wino.academyapi.domain.student.dto.StudentSiblingDtos$SiblingLinkDto(
            sb.id, s.id, s.name, s.schoolStage, s.workLocationCode, s.status, sb.relationNote
        )
        from StudentSibling sb
        join sb.high s
        where sb.low.id = :studentId
        order by s.name
    """)
    List<SiblingLinkDto> findSiblingsAsLow(@Param("studentId") Long studentId);
}