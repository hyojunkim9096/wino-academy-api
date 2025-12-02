// src/main/java/com/wino/academyapi/domain/tuition/repository/StudentTuitionRepository.java
package com.wino.academyapi.domain.tuition.repository;

import com.wino.academyapi.domain.tuition.entity.StudentTuition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 학생 수강료 등록 리포지토리 */
public interface StudentTuitionRepository extends JpaRepository<StudentTuition, Long> {

    List<StudentTuition> findByStudentIdOrderByIdAsc(Long studentId);

    boolean existsByStudentIdAndTuitionPriceIdAndActiveTrue(Long studentId, Long tuitionPriceId);

    /** 동일 학생 소유 체크용 */
    Optional<StudentTuition> findByIdAndStudentId(Long id, Long studentId);

    /** 활성 중복 가드(본인 제외) */
    boolean existsByStudentIdAndTuitionPriceIdAndActiveTrueAndIdNot(Long studentId, Long tuitionPriceId, Long id);
}