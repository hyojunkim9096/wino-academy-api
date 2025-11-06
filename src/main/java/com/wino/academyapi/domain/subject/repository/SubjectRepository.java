// src/main/java/com/wino/academyapi/domain/subject/repository/SubjectRepository.java
package com.wino.academyapi.domain.subject.repository;

import com.wino.academyapi.domain.subject.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findBySchoolStageAndParentIdOrderBySortOrderAsc(String schoolStage, Long parentId);
}
