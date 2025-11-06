// src/main/java/com/wino/academyapi/domain/consult/repository/ConsultNoteRepository.java
package com.wino.academyapi.domain.consult.repository;

import com.wino.academyapi.domain.consult.entity.ConsultNote;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ConsultNoteRepository extends JpaRepository<ConsultNote, Long> {

    /** 학생별 목록 (학생 상세 화면 최적화 뷰) */
    @Query("""
        select c from ConsultNote c
        where c.student.id = :sid
        order by c.consultAt desc, c.id desc
    """)
    Page<ConsultNote> findByStudent(@Param("sid") Long studentId, Pageable pageable);

    /** 상단 컬렉션 검색 — 학생/작성자/기간 필터 */
    @Query("""
        select c from ConsultNote c
        where (:sid is null or c.student.id = :sid)
          and (:wid is null or c.writerId = :wid)
          and (:fromAt is null or c.consultAt >= :fromAt)
          and (:toAt   is null or c.consultAt <  :toAt)
        order by c.consultAt desc, c.id desc
    """)
    Page<ConsultNote> search(@Param("sid") Long studentId,
                             @Param("wid") Long writerId,
                             @Param("fromAt") LocalDateTime from,
                             @Param("toAt") LocalDateTime to,
                             Pageable pageable);
}