package com.wino.academyapi.domain.admin.team.repository;

import com.wino.academyapi.domain.admin.team.dto.TeamDtos.TeamSummaryRes;
import com.wino.academyapi.domain.admin.team.entity.TeamGroup;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TeamGroupRepository extends JpaRepository<TeamGroup, Long>, JpaSpecificationExecutor<TeamGroup> {

    /**
     * @deprecated N+1 쿼리 유발 (서비스에서 루프 돌며 멤버 수 카운트).
     * searchWithSummary() 사용을 권장합니다.
     */
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
    @Deprecated
    Page<TeamGroup> search(
            @Param("status") String status,
            @Param("workLoc") String workLocation,
            @Param("keyword") String keyword,
            Pageable pageable
    );


    // ========================================================================
    // ✅ [JPQL 구문 오류 수정]
    // 중첩 클래스(TeamDtos 안에 있는 TeamSummaryRes)를 JPQL의 'select new' 구문에서
    // 사용하려면 '.' 대신 '$'를 사용해야 합니다.
    //
    // (변경 전) ...new com.wino.academyapi.domain.admin.team.dto.TeamDtos.TeamSummaryRes(
    // (변경 후) ...new com.wino.academyapi.domain.admin.team.dto.TeamDtos$TeamSummaryRes(
    // ========================================================================
    @Query(
            value = """
            select new com.wino.academyapi.domain.admin.team.dto.TeamDtos$TeamSummaryRes(
                 tg.id,
                 tg.teamCode,
                 tg.teamName,
                 tg.description,
                 tg.workLocation,
                 tg.status,
                 tg.leader.id,
                 l.userName, 
                 (select count(tm) from TeamMember tm where tm.team.id = tg.id and tm.activeYn='Y'),
                 tg.createdAt,
                 tg.updatedAt
            )
              from TeamGroup tg
         left join tg.leader l 
             where (:status is null or tg.status = :status)
               and (:workLoc is null or tg.workLocation = :workLoc)
               and (
                    :keyword is null
                 or lower(tg.teamName) like lower(concat('%', :keyword, '%'))
                 or lower(tg.teamCode) like lower(concat('%', :keyword, '%'))
               )
        """,
            countQuery = """
            select count(tg)
              from TeamGroup tg
             where (:status is null or tg.status = :status)
               and (:workLoc is null or tg.workLocation = :workLoc)
               and (
                    :keyword is null
                 or lower(tg.teamName) like lower(concat('%', :keyword, '%'))
                 or lower(tg.teamCode) like lower(concat('%', :keyword, '%'))
               )
        """
    )
    Page<TeamSummaryRes> searchWithSummary(
            @Param("status") String status,
            @Param("workLoc") String workLocation,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}