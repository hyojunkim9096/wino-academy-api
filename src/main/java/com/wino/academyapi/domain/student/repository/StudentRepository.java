// src/main/java/com/wino/academyapi/domain/student/repository/StudentRepository.java
package com.wino.academyapi.domain.student.repository;

import com.wino.academyapi.domain.student.entity.Student;
import com.wino.academyapi.domain.student.dto.StudentDtos.StudentSummary;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * ✅ [수정] DTO 프로젝션 및 N+1 해결
 * - Student, EndUserStudentMap, EndUser, School, StudentMemo(subquery)를 조인
 * - ✅ [수정] 'gender' 필드 조회 추가
 * - 정렬: 이름(lower asc) → id desc
 */
public interface StudentRepository extends JpaRepository<Student, Long> {

    @Query(value = """
            select new com.wino.academyapi.domain.student.dto.StudentDtos$StudentSummary(
                s.id,
                u.id, 
                u.loginId,
                s.workLocationCode,
                s.schoolStage,
                s.status,
                s.name,
                s.birthdate,
                s.gender,
                s.schoolId,
                sch.name, 
                s.gradeLabel,
                s.phone,
                s.email,
                s.preferSms,
                s.preferEmail,
                s.preferPush,
                s.pushUserKey,
                s.postalCode,
                s.address,
                s.detailAddress,
                img.id,
                null, 
                null, 
                (select sm.content from StudentMemo sm where sm.student.id = s.id order by sm.pinned desc, sm.createdAt desc limit 1),
                null, null, null, null 
            )
            from Student s
             left join s.endUserMap m
             left join m.user u
             left join School sch on sch.id = s.schoolId
             left join s.profileImage img
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
             left join s.endUserMap m
             left join m.user u
             where (:stage is null or s.schoolStage = :stage)
               and (:wl    is null or s.workLocationCode = :wl)
               and (:kw    is null
                    or lower(s.name)    like lower(concat('%', :kw, '%'))
                    or lower(s.phone)   like lower(concat('%', :kw, '%'))
                    or lower(s.email)   like lower(concat('%', :kw, '%'))
                    or lower(u.loginId) like lower(concat('%', :kw, '%')))
        """)
        // ✅ [수정] 반환 타입을 Page<StudentSummary>로 변경
    Page<StudentSummary> searchWithSummary(
            @Param("stage") String schoolStage,
            @Param("wl") String workLocationCode,
            @Param("kw") String keyword,
            Pageable pageable
    );

    // ✅ [수정] DDL(end_user_student_map) 변경에 맞춰 JOIN 구문 수정
    @Query(value = """
            select s from Student s
             left join s.endUserMap m 
             left join m.user u 
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
             left join s.endUserMap m 
             left join m.user u 
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