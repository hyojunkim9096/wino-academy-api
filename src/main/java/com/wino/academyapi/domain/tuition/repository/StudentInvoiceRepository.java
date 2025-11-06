package com.wino.academyapi.domain.tuition.repository;

import com.wino.academyapi.domain.tuition.entity.StudentInvoice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** 학생 청구서 리포지토리 */
public interface StudentInvoiceRepository extends JpaRepository<StudentInvoice, Long> {

    /** 최근 N개(월 최신 → id 역순) */
    @Query("""
        select i from StudentInvoice i
         where i.studentId = :studentId
         order by i.billMonth desc, i.id desc
    """)
    List<StudentInvoice> findRecent(Long studentId, Pageable pageable);

    boolean existsByStudentIdAndTuitionPriceIdAndBillMonth(Long studentId, Long priceId, String billMonth);

    /** 특정 등록(tuition_id)로 생성된 청구가 존재하는지 */
    boolean existsByTuitionId(Long tuitionId);
}