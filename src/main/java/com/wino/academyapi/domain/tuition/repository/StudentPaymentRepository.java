// src/main/java/com/wino/academyapi/domain/tuition/repository/StudentPaymentRepository.java
package com.wino.academyapi.domain.tuition.repository;

import com.wino.academyapi.domain.tuition.entity.StudentPayment;
import org.springframework.data.jpa.repository.JpaRepository;

/** 학생 결제 리포지토리 */
public interface StudentPaymentRepository extends JpaRepository<StudentPayment, Long> {
}