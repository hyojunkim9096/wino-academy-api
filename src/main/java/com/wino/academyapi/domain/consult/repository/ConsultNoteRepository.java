package com.wino.academyapi.domain.consult.repository;

import com.wino.academyapi.domain.consult.entity.ConsultNote;
// ✅ DTO 프로젝션을 위해 import
import com.wino.academyapi.domain.consult.dto.ConsultDtos.ConsultSummary;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담(ConsultNote) 리포지토리
 *
 * ✅ [리팩토링 완료]
 * - search 쿼리 내 'ClassMaster' -> 'Course' 로 엔티티명 변경 반영
 * - DTO 프로젝션 사용 (학생명, 반명, 담임명, 작성자명 포함)
 * - 권한: 조회는 전체 허용 (WHERE 절에서 권한 로직 제거)
 * - 정렬: 미승인(homeroomOk=false) 우선, 그 다음 최신순
 * - 중복제거: 학생-반 JOIN 시 'MAIN' 반만 JOIN
 */
public interface ConsultNoteRepository extends JpaRepository<ConsultNote, Long> {

    /** 학생별 목록 (학생 상세 화면 최적화 뷰) - 권한 검사 없음 */
    @Query("""
        select c from ConsultNote c
        where c.student.id = :sid
        order by c.consultAt desc, c.id desc
    """)
    Page<ConsultNote> findByStudent(@Param("sid") Long studentId, Pageable pageable);

    /**
     * ✅ [수정] JPQL 내 'ClassMaster' -> 'Course' 로 변경
     */
    @Query(value = """
        select new com.wino.academyapi.domain.consult.dto.ConsultDtos$ConsultSummary(
            c.id,
            s.id,
            s.name,
            cm.id,
            cm.name,
            ht.id,
            ht.userName,
            c.writerId,
            auWriter.userName,
            c.homeroomOk,
            c.consultMethod,
            c.consultType,
            c.title,
            c.content,
            c.actionPlan,
            c.consultAt,
            c.nextFollowupAt,
            c.visibilityRole,
            c.useYn,
            c.createdAt,
            c.updatedAt,
            null
        )
        from ConsultNote c
        join c.student s
        left join AdminUser auWriter on auWriter.id = c.writerId
        left join StudentClassEnrollment e on e.student.id = s.id and e.status = 'ACTIVE' and e.classStatusCode = 'MAIN'
        left join Course cm on cm.id = e.classId 
        left join AdminUser ht on ht.id = cm.homeroomTeacherId
        where
            (:sid    is null or c.student.id = :sid)
          and (:wid    is null or c.writerId = :wid)
          and (:fromAt is null or c.consultAt >= :fromAt)
          and (:toAt   is null or c.consultAt <  :toAt)
          and (:sName is null or s.name like concat('%', :sName, '%'))
          and (:wName is null or auWriter.userName like concat('%', :wName, '%'))
        order by c.homeroomOk asc, c.consultAt desc, c.id desc
    """,
            countQuery = """
        select count(c) 
        from ConsultNote c
        join c.student s
        left join AdminUser auWriter on auWriter.id = c.writerId
        left join StudentClassEnrollment e on e.student.id = s.id and e.status = 'ACTIVE' and e.classStatusCode = 'MAIN'
        where
            (:sid    is null or c.student.id = :sid)
          and (:wid    is null or c.writerId = :wid)
          and (:fromAt is null or c.consultAt >= :fromAt)
          and (:toAt   is null or c.consultAt <  :toAt)
          and (:sName is null or s.name like concat('%', :sName, '%'))
          and (:wName is null or auWriter.userName like concat('%', :wName, '%'))
    """)
    Page<ConsultSummary> searchWithPermissions(
            @Param("sid") Long studentId,
            @Param("sName") String studentName,
            @Param("wid") Long writerId,
            @Param("wName") String writerName,
            @Param("fromAt") LocalDateTime from,
            @Param("toAt") LocalDateTime to,

            Pageable pageable
    );
}