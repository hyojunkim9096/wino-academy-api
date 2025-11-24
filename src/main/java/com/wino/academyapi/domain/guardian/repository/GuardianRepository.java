// src/main/java/com/wino/academyapi/domain/guardian/repository/GuardianRepository.java
package com.wino.academyapi.domain.guardian.repository;

import com.wino.academyapi.domain.guardian.dto.GuardianDtos.GuardianSummary;
import com.wino.academyapi.domain.guardian.entity.Guardian;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * 보호자 기본 리포지토리
 * - 엔티티 조회 + Summary DTO 프로젝션 쿼리 제공
 */
public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    /**
     * DTO 프로젝션 + 계정(EndUser) JOIN으로 N+1 문제 해결
     * - g.endUserMap (엔티티 1:1 연관관계)을 통해 조인
     */
    @Query(value = """
        select new com.wino.academyapi.domain.guardian.dto.GuardianDtos$GuardianSummary(
            g.id, g.name, g.phone, g.email,
            g.preferSms, g.preferEmail, g.preferPush, g.pushUserKey,
            g.postalCode, g.address, g.detailAddress, g.memo,
            u.id, u.loginId, u.status
        )
        from Guardian g
        left join g.endUserMap m
        left join m.user u
        where (:kw is null
               or lower(g.name)  like lower(concat('%', :kw, '%'))
               or lower(g.phone) like lower(concat('%', :kw, '%'))
               or lower(g.email) like lower(concat('%', :kw, '%'))
               or lower(u.loginId) like lower(concat('%', :kw, '%'))
        )
        order by lower(g.name) asc, g.id desc
    """,
            countQuery = """
        select count(g)
        from Guardian g
        left join g.endUserMap m
        left join m.user u
        where (:kw is null
               or lower(g.name)  like lower(concat('%', :kw, '%'))
               or lower(g.phone) like lower(concat('%', :kw, '%'))
               or lower(g.email) like lower(concat('%', :kw, '%'))
               or lower(u.loginId) like lower(concat('%', :kw, '%'))
        )
    """)
    Page<GuardianSummary> searchWithSummary(
            @Param("kw") String keyword,
            Pageable pageable
    );

    /**
     * 기존 엔티티 직접 검색 쿼리 (하위 호환용)
     */
    @Query(value = """
        select g
        from Guardian g
        where (:kw is null
               or lower(g.name)  like lower(concat('%', :kw, '%'))
               or lower(g.phone) like lower(concat('%', :kw, '%'))
               or lower(g.email) like lower(concat('%', :kw, '%'))
        )
        order by lower(g.name) asc, g.id desc
    """,
            countQuery = """
        select count(g)
        from Guardian g
        where (:kw is null
               or lower(g.name)  like lower(concat('%', :kw, '%'))
               or lower(g.phone) like lower(concat('%', :kw, '%'))
               or lower(g.email) like lower(concat('%', :kw, '%'))
        )
    """)
    Page<Guardian> search(@Param("kw") String keyword, Pageable pageable);
}
