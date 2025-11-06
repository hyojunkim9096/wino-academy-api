// src/main/java/com/wino/academyapi/domain/consult/repository/ConsultNoteGuardianRepository.java
package com.wino.academyapi.domain.consult.repository;

import com.wino.academyapi.domain.consult.entity.ConsultNoteGuardian;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultNoteGuardianRepository extends JpaRepository<ConsultNoteGuardian, Long> {
    List<ConsultNoteGuardian> findByConsult_Id(Long consultId);
    void deleteByConsult_Id(Long consultId);
}