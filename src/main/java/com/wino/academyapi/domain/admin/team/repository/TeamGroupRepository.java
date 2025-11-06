package com.wino.academyapi.domain.admin.team.repository;

import com.wino.academyapi.domain.admin.team.entity.TeamGroup;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TeamGroupRepository extends JpaRepository<TeamGroup, Long>, JpaSpecificationExecutor<TeamGroup> {

    @Query("""
        select tg
          from TeamGroup tg
         where (:status is null or tg.status = :status)
           and (:workLoc is null or tg.workLocation = :workLoc)
           and (
                :keyword is null
             or lower(tg.teamName) like lower(concat('%', :keyword, '%'))
             or lower(tg.teamCode) like lower(concat('%', :keyword, '%'))
           )
         order by lower(tg.teamName) asc, tg.id desc
    """)
    Page<TeamGroup> search(
            @Param("status") String status,
            @Param("workLoc") String workLocation,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}