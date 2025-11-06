// src/main/java/com/wino/academyapi/domain/student/repository/StudentHistRepository.java
package com.wino.academyapi.domain.student.repository;

import com.wino.academyapi.domain.student.entity.StudentHist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * student_hist 조회 리포지토리
 * - 최초/최신 이력 빠르게 가져오기 위한 메서드 제공
 */
public interface StudentHistRepository extends JpaRepository<StudentHist, Long> {

    /** 최초 이력: ref_id 기준 version 오름차순 1건 */
    Optional<StudentHist> findFirstByRefIdOrderByVersionAsc(Long refId);

    /** 최신 이력: ref_id 기준 version 내림차순 1건 */
    Optional<StudentHist> findFirstByRefIdOrderByVersionDesc(Long refId);
}