// src/main/java/com/wino/academyapi/domain/enduser/repository/EndUserRepository.java
package com.wino.academyapi.domain.enduser.repository;

import com.wino.academyapi.domain.enduser.entity.EndUser;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface EndUserRepository extends JpaRepository<EndUser, Long> {

    /** 로그인 아이디(대소문자 무시)로 조회 — UNIQUE */
    @Query("select e from EndUser e where lower(e.loginId) = lower(:loginId)")
    Optional<EndUser> findByLoginIdIgnoreCase(@Param("loginId") String loginId);

    List<EndUser> findByIdIn(Collection<Long> ids);
}