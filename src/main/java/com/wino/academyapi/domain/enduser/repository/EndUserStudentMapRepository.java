// src/main/java/com/wino/academyapi/domain/enduser/repository/EndUserStudentMapRepository.java
package com.wino.academyapi.domain.enduser.repository;

import com.wino.academyapi.domain.enduser.entity.EndUserStudentMap;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface EndUserStudentMapRepository extends JpaRepository<EndUserStudentMap, Long> {

    Optional<EndUserStudentMap> findByStudentId(Long studentId);

    List<EndUserStudentMap> findByStudentIdIn(Collection<Long> studentIds);

    Optional<EndUserStudentMap> findByUserId(Long userId);

    void deleteByStudentId(Long studentId);
}