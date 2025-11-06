// src/main/java/com/wino/academyapi/domain/student/repository/StudentRepository.java
package com.wino.academyapi.domain.student.repository;

import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.enduser.entity.EndUser;
import com.wino.academyapi.domain.enduser.entity.EndUserStudentMap;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * ✅ 키워드에 end_user.login_id 포함시키기 위해 JPQL ON JOIN 사용(JPA 2.1+ / Hibernate 지원)
 *  - Student, EndUserStudentMap, EndUser 은 엔티티 스캔 대상이어야 함
 *  - 정렬: 이름(lower asc) → id desc
 */
public interface StudentRepository extends JpaRepository<Student, Long> {

    @Query(value = """
            select s from Student s
             left join EndUserStudentMap m on m.studentId = s.id
             left join EndUser u on u.id = m.userId
             where (:stage is null or s.schoolStage = :stage)
               and (:wl    is null or s.workLocationCode = :wl)
               and (:kw    is null
                    or lower(s.name)    like lower(concat('%', :kw, '%'))
                    or lower(s.phone)   like lower(concat('%', :kw, '%'))
                    or lower(s.email)   like lower(concat('%', :kw, '%'))
                    or lower(u.loginId) like lower(concat('%', :kw, '%')))
             order by lower(s.name) asc, s.id desc
        """,
            countQuery = """
            select count(s) from Student s
             left join EndUserStudentMap m on m.studentId = s.id
             left join EndUser u on u.id = m.userId
             where (:stage is null or s.schoolStage = :stage)
               and (:wl    is null or s.workLocationCode = :wl)
               and (:kw    is null
                    or lower(s.name)    like lower(concat('%', :kw, '%'))
                    or lower(s.phone)   like lower(concat('%', :kw, '%'))
                    or lower(s.email)   like lower(concat('%', :kw, '%'))
                    or lower(u.loginId) like lower(concat('%', :kw, '%')))
        """)
    Page<Student> search(
            @Param("stage") String schoolStage,
            @Param("wl") String workLocationCode,
            @Param("kw") String keyword,
            Pageable pageable
    );
}