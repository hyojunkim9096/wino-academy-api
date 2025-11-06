// src/main/java/com/wino/academyapi/domain/guardian/repository/GuardianRepository.java
package com.wino.academyapi.domain.guardian.repository;

import com.wino.academyapi.domain.guardian.entity.Guardian;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    @Query(value = """
            select g from Guardian g
             where (:kw is null
                    or lower(g.name)  like lower(concat('%', :kw, '%'))
                    or lower(g.phone) like lower(concat('%', :kw, '%'))
                    or lower(g.email) like lower(concat('%', :kw, '%')))
             order by lower(g.name) asc, g.id desc
        """,
            countQuery = """
            select count(g) from Guardian g
             where (:kw is null
                    or lower(g.name)  like lower(concat('%', :kw, '%'))
                    or lower(g.phone) like lower(concat('%', :kw, '%'))
                    or lower(g.email) like lower(concat('%', :kw, '%')))
        """)
    Page<Guardian> search(@Param("kw") String keyword, Pageable pageable);
}